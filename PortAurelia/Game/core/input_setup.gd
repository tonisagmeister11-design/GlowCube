extends Node
## Registers all input actions (keyboard + mouse + controller) at startup.
## Keeping the bindings in code makes them easy to read, extend and rebind.

const KEYS := {
	"move_forward": [KEY_W], "move_back": [KEY_S], "move_left": [KEY_A], "move_right": [KEY_D],
	"sprint": [KEY_SHIFT], "jump": [KEY_SPACE], "crouch": [KEY_CTRL, KEY_C],
	"reload": [KEY_R], "interact": [KEY_E], "vehicle_enter": [KEY_F],
	"map": [KEY_M], "pause": [KEY_ESCAPE], "weapon_wheel": [KEY_TAB],
	"phone": [KEY_UP, KEY_P], "horn": [KEY_H], "headlights": [KEY_L], "siren": [KEY_G],
	"camera_mode": [KEY_V], "look_behind": [KEY_B], "cover": [KEY_Q],
	"handbrake": [KEY_SPACE], "accelerate": [KEY_W], "brake": [KEY_S],
	"steer_left": [KEY_A], "steer_right": [KEY_D],
	"ui_phone_up": [KEY_UP], "ui_phone_down": [KEY_DOWN],
	"quick_save": [KEY_F5],
	"debug_hud": [KEY_F1], "debug_spawn_vehicle": [KEY_F2], "debug_spawn_npc": [KEY_F3],
	"debug_give_weapon": [KEY_F4], "debug_wanted": [KEY_F6], "debug_teleport": [KEY_F7],
	"debug_mission": [KEY_F8], "debug_toggle_ai": [KEY_F9], "debug_perf": [KEY_F10],
	"debug_time": [KEY_F11],
}

const MOUSE := {
	"fire": MOUSE_BUTTON_LEFT, "aim": MOUSE_BUTTON_RIGHT,
	"weapon_next": MOUSE_BUTTON_WHEEL_UP, "weapon_prev": MOUSE_BUTTON_WHEEL_DOWN,
}

const PAD_BUTTONS := {
	"jump": JOY_BUTTON_A, "sprint": JOY_BUTTON_A, "crouch": JOY_BUTTON_LEFT_STICK,
	"reload": JOY_BUTTON_B, "interact": JOY_BUTTON_RIGHT_SHOULDER, "vehicle_enter": JOY_BUTTON_Y,
	"map": JOY_BUTTON_BACK, "pause": JOY_BUTTON_START, "weapon_wheel": JOY_BUTTON_LEFT_SHOULDER,
	"phone": JOY_BUTTON_DPAD_UP, "horn": JOY_BUTTON_LEFT_STICK, "handbrake": JOY_BUTTON_RIGHT_SHOULDER,
	"camera_mode": JOY_BUTTON_BACK, "cover": JOY_BUTTON_RIGHT_SHOULDER, "look_behind": JOY_BUTTON_RIGHT_STICK,
	"headlights": JOY_BUTTON_DPAD_RIGHT, "weapon_next": JOY_BUTTON_DPAD_RIGHT, "weapon_prev": JOY_BUTTON_DPAD_LEFT,
}

const PAD_AXES := {
	"move_forward": [JOY_AXIS_LEFT_Y, -1.0], "move_back": [JOY_AXIS_LEFT_Y, 1.0],
	"move_left": [JOY_AXIS_LEFT_X, -1.0], "move_right": [JOY_AXIS_LEFT_X, 1.0],
	"steer_left": [JOY_AXIS_LEFT_X, -1.0], "steer_right": [JOY_AXIS_LEFT_X, 1.0],
	"fire": [JOY_AXIS_TRIGGER_RIGHT, 1.0], "aim": [JOY_AXIS_TRIGGER_LEFT, 1.0],
	"accelerate": [JOY_AXIS_TRIGGER_RIGHT, 1.0], "brake": [JOY_AXIS_TRIGGER_LEFT, 1.0],
	"look_left": [JOY_AXIS_RIGHT_X, -1.0], "look_right": [JOY_AXIS_RIGHT_X, 1.0],
	"look_up": [JOY_AXIS_RIGHT_Y, -1.0], "look_down": [JOY_AXIS_RIGHT_Y, 1.0],
}


func _enter_tree() -> void:
	for action in KEYS:
		_ensure(action)
		for k in KEYS[action]:
			var ev := InputEventKey.new()
			ev.physical_keycode = k
			InputMap.action_add_event(action, ev)
	for action in MOUSE:
		_ensure(action)
		var mb := InputEventMouseButton.new()
		mb.button_index = MOUSE[action]
		InputMap.action_add_event(action, mb)
	for i in range(1, 10):
		var action := "weapon_%d" % i
		_ensure(action)
		var ev := InputEventKey.new()
		ev.physical_keycode = KEY_0 + i
		InputMap.action_add_event(action, ev)
	for action in PAD_BUTTONS:
		_ensure(action)
		var jb := InputEventJoypadButton.new()
		jb.button_index = PAD_BUTTONS[action]
		InputMap.action_add_event(action, jb)
	for action in PAD_AXES:
		_ensure(action)
		var jm := InputEventJoypadMotion.new()
		jm.axis = PAD_AXES[action][0]
		jm.axis_value = PAD_AXES[action][1]
		InputMap.action_add_event(action, jm)


func _ensure(action: String) -> void:
	if not InputMap.has_action(action):
		InputMap.add_action(action, 0.2)
