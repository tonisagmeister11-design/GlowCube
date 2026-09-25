class_name ShaderGlobals
extends RefCounted
## Script-side mirror of the global shader uniforms (night, wetness, wind, city_lights,
## sun_dir). RenderingServer can only read them back in the editor, so gameplay code
## reads the values from here.

static var values := {}


static func set_value(param: String, v) -> void:
	values[param] = v
	RenderingServer.global_shader_parameter_set(param, v)


static func get_value(param: String, default = 0.0):
	return values.get(param, default)
