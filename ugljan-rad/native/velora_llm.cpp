// Velora Soul: kleiner JNI-Wrapper um llama.cpp. Laeuft komplett auf dem Handy.
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <atomic>
#include <mutex>
#include "llama.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "VeloraLLM", __VA_ARGS__)

struct Engine {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    std::vector<llama_token> cached;   // Tokens, die gerade im KV-Cache liegen
    std::atomic<bool> stop{false};
    std::mutex mu;
};

static void log_cb(ggml_log_level lvl, const char *txt, void *) {
    if (lvl >= GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, "VeloraLLM", "%s", txt);
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_velora_bike_Llm_nLoad(JNIEnv *env, jclass, jstring jpath, jint nCtx, jint nThreads) {
    static bool inited = false;
    if (!inited) { llama_log_set(log_cb, nullptr); llama_backend_init(); inited = true; }
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mp = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) return 0;
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = nCtx;
    cp.n_batch = 512;
    cp.n_ubatch = 512;
    cp.n_threads = nThreads;
    cp.n_threads_batch = nThreads;
    cp.no_perf = true;
    llama_context *ctx = llama_init_from_model(model, cp);
    if (!ctx) { llama_model_free(model); return 0; }
    Engine *e = new Engine();
    e->model = model; e->ctx = ctx; e->vocab = llama_model_get_vocab(model);
    LOG("geladen: ctx=%d threads=%d", nCtx, nThreads);
    return (jlong) e;
}

extern "C" JNIEXPORT void JNICALL
Java_app_velora_bike_Llm_nFree(JNIEnv *, jclass, jlong h) {
    Engine *e = (Engine *) h;
    if (!e) return;
    e->stop = true;
    std::lock_guard<std::mutex> lk(e->mu);
    llama_free(e->ctx);
    llama_model_free(e->model);
    delete e;
}

extern "C" JNIEXPORT void JNICALL
Java_app_velora_bike_Llm_nStop(JNIEnv *, jclass, jlong h) {
    Engine *e = (Engine *) h;
    if (e) e->stop = true;
}

// Erzeugt Text. cb.onToken(byte[]) bekommt rohe UTF-8-Stuecke, gibt false zurueck zum Abbrechen.
// Rueckgabe: Anzahl erzeugter Tokens, negativ bei Fehler.
extern "C" JNIEXPORT jint JNICALL
Java_app_velora_bike_Llm_nGenerate(JNIEnv *env, jclass, jlong h, jstring jprompt, jint nPredict,
                                   jfloat temp, jfloat topP, jint topK, jfloat rep, jobject cb) {
    Engine *e = (Engine *) h;
    if (!e) return -1;
    std::lock_guard<std::mutex> lk(e->mu);
    e->stop = false;
    jclass cbc = env->GetObjectClass(cb);
    jmethodID onTok = env->GetMethodID(cbc, "onToken", "([B)Z");

    const char *p = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(p);
    env->ReleaseStringUTFChars(jprompt, p);

    const int nCtx = (int) llama_n_ctx(e->ctx);
    int n = -llama_tokenize(e->vocab, prompt.c_str(), (int) prompt.size(), nullptr, 0, true, true);
    std::vector<llama_token> toks(n);
    if (llama_tokenize(e->vocab, prompt.c_str(), (int) prompt.size(), toks.data(), n, true, true) < 0) return -2;
    // Zu lang: vorne kuerzen ist bei Chat-Prompts schlecht, deshalb kuerzt die JS-Seite selbst.
    if ((int) toks.size() + nPredict + 8 > nCtx) {
        int keep = nCtx - nPredict - 8;
        if (keep < 64) return -3;
        toks.erase(toks.begin(), toks.end() - keep);
    }

    // Gemeinsamen Anfang mit dem Cache wiederverwenden (System-Prompt bleibt gleich -> schnell).
    size_t common = 0;
    while (common < e->cached.size() && common < toks.size() && e->cached[common] == toks[common]) common++;
    if (common == toks.size()) common--;  // mindestens ein Token neu auswerten fuer Logits
    llama_memory_t mem = llama_get_memory(e->ctx);
    if (!llama_memory_seq_rm(mem, 0, (llama_pos) common, -1)) {
        llama_memory_clear(mem, true);
        common = 0;
    }
    e->cached.resize(common);

    const int nb = 512;
    for (size_t i = common; i < toks.size(); i += nb) {
        if (e->stop) return 0;
        int cnt = (int) std::min((size_t) nb, toks.size() - i);
        if (llama_decode(e->ctx, llama_batch_get_one(toks.data() + i, cnt)) != 0) {
            llama_memory_clear(mem, true); e->cached.clear(); return -4;
        }
        e->cached.insert(e->cached.end(), toks.begin() + i, toks.begin() + i + cnt);
    }

    llama_sampler *s = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(s, llama_sampler_init_penalties(llama_vocab_n_tokens(e->vocab), 64, rep, 0.0f, 0.0f));
    llama_sampler_chain_add(s, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(s, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(s, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(s, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(s, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int produced = 0;
    char buf[256];
    while (produced < nPredict && !e->stop) {
        if ((int) e->cached.size() + 1 >= nCtx) break;
        llama_token t = llama_sampler_sample(s, e->ctx, -1);
        if (llama_vocab_is_eog(e->vocab, t)) break;
        int len = llama_token_to_piece(e->vocab, t, buf, sizeof(buf), 0, false);
        if (len > 0) {
            jbyteArray arr = env->NewByteArray(len);
            env->SetByteArrayRegion(arr, 0, len, (const jbyte *) buf);
            jboolean more = env->CallBooleanMethod(cb, onTok, arr);
            env->DeleteLocalRef(arr);
            if (!more) e->stop = true;
        }
        produced++;
        e->cached.push_back(t);
        if (llama_decode(e->ctx, llama_batch_get_one(&t, 1)) != 0) { e->cached.pop_back(); break; }
    }
    llama_sampler_free(s);
    return produced;
}
