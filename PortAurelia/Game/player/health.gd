class_name Health
extends Node
## Health + armour component used by the player and NPCs.

signal damaged(amount: float, source: Node, hit_pos: Vector3, dir: Vector3)
signal died(source: Node)
signal changed(health: float, armor: float)

@export var max_health := 100.0
@export var max_armor := 100.0
@export var regen_to := 0.5        # fraction of max health regenerated automatically
@export var regen_rate := 2.0      # hp per second
@export var regen_delay := 6.0

var health := 100.0
var armor := 0.0
var dead := false
var invulnerable := false
var _since_hit := 99.0


func _ready() -> void:
	health = max_health


func _process(delta: float) -> void:
	_since_hit += delta
	if dead or _since_hit < regen_delay:
		return
	var cap := max_health * regen_to
	if health < cap:
		health = minf(cap, health + regen_rate * delta)
		changed.emit(health, armor)


func take_damage(amount: float, source: Node = null, hit_pos := Vector3.ZERO, dir := Vector3.ZERO) -> void:
	if dead or invulnerable or amount <= 0.0:
		return
	_since_hit = 0.0
	var absorbed := minf(armor, amount * 0.7)
	armor -= absorbed
	health -= amount - absorbed
	damaged.emit(amount, source, hit_pos, dir)
	if health <= 0.0:
		health = 0.0
		dead = true
		died.emit(source)
	changed.emit(health, armor)


func heal(amount: float) -> void:
	if dead:
		return
	health = minf(max_health, health + amount)
	changed.emit(health, armor)


func add_armor(amount: float) -> void:
	armor = minf(max_armor, armor + amount)
	changed.emit(health, armor)


func revive(fraction := 1.0) -> void:
	dead = false
	health = max_health * fraction
	changed.emit(health, armor)


func fraction() -> float:
	return health / max_health
