package no.vardir.skald.core.graph

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The constellation is a place you return to, not a simulation that re-renders
 * every time it opens. Positions are normalized to roughly [0.03, 0.97] and
 * persisted; only nodes without a stored position are laid out, and stored ones
 * act as fixed anchors.
 *
 * Each note carries its top-level folder as a group, and each group gets a home
 * on the map, so a fresh layout reads as folders rather than as one cloud.
 */
object Layout {

    data class Point(val x: Float, val y: Float)

    private class Node(
        val id: String,
        val group: String,
        val fixed: Boolean,
        var x: Float,
        var y: Float,
    )

    /** Deterministic FNV-1a derived hash in [0,1). */
    private fun hash01(s: String, salt: Int): Float {
        var h = 2166136261u.toInt() xor salt
        for (ch in s) {
            h = h xor ch.code
            h *= 16777619
        }
        return ((h.toUInt() % 100000u).toInt()) / 100000f
    }

    private fun groupHome(group: String, ordinal: Int, total: Int): Point {
        if (group.isEmpty()) return Point(0.5f, 0.5f)
        val spread = max(1, total)
        val angle = (ordinal.toFloat() / spread) * PI.toFloat() * 2f + hash01(group, 977) * 0.4f
        val radius = if (total <= 1) 0f else 0.26f + 0.06f * hash01(group, 41)
        return Point(0.5f + cos(angle) * radius, 0.5f + sin(angle) * radius * 0.86f)
    }

    private fun clamp01(v: Float): Float = max(0.03f, min(0.97f, v))

    fun layout(
        ids: List<String>,
        edges: List<Pair<String, String>>,
        stored: Map<String, Point>,
        groups: Map<String, String> = emptyMap(),
    ): Map<String, Point> {
        val groupNames = ids.map { groups[it] ?: "" }.filter { it.isNotEmpty() }.distinct().sorted()
        val homes = groupNames.withIndex().associate { (i, name) ->
            name to groupHome(name, i, groupNames.size)
        }

        val nodes = ids.map { id ->
            val group = groups[id] ?: ""
            val p = stored[id]
            if (p != null && p.x.isFinite() && p.y.isFinite()) {
                Node(id, group, fixed = true, x = clamp01(p.x), y = clamp01(p.y))
            } else {
                // Seed inside the group's neighbourhood; the simulation refines it.
                val home = homes[group] ?: Point(0.5f, 0.5f)
                val scatter = if (homes.isNotEmpty()) 0.11f else 0.35f
                Node(
                    id, group, fixed = false,
                    x = clamp01(home.x + (hash01(id, 7) - 0.5f) * 2f * scatter),
                    y = clamp01(home.y + (hash01(id, 131) - 0.5f) * 2f * scatter),
                )
            }
        }

        if (nodes.none { !it.fixed }) return nodes.associate { it.id to Point(it.x, it.y) }

        val index = nodes.withIndex().associate { (i, n) -> n.id to i }
        val adjacency = edges.mapNotNull { (a, b) ->
            val ia = index[a]
            val ib = index[b]
            if (ia != null && ib != null && ia != ib) ia to ib else null
        }

        val iterations = 160
        val repulse = 0.0035f
        val spring = 0.02f
        val rest = 0.16f
        val groupPull = 0.022f
        val crossGroupPush = 1.6f

        val fx = FloatArray(nodes.size)
        val fy = FloatArray(nodes.size)

        for (it in 0 until iterations) {
            val cool = 1f - it.toFloat() / iterations
            fx.fill(0f)
            fy.fill(0f)

            for (i in nodes.indices) {
                for (j in i + 1 until nodes.size) {
                    var dx = nodes[i].x - nodes[j].x
                    var dy = nodes[i].y - nodes[j].y
                    var d2 = dx * dx + dy * dy
                    if (d2 < 1e-6f) {
                        dx = (hash01(nodes[i].id + nodes[j].id, it) - 0.5f) * 0.01f
                        dy = (hash01(nodes[j].id + nodes[i].id, it) - 0.5f) * 0.01f
                        d2 = dx * dx + dy * dy + 1e-6f
                    }
                    val apart = if (nodes[i].group != nodes[j].group) crossGroupPush else 1f
                    val f = (repulse * apart) / d2
                    val d = sqrt(d2)
                    fx[i] += (dx / d) * f
                    fy[i] += (dy / d) * f
                    fx[j] -= (dx / d) * f
                    fy[j] -= (dy / d) * f
                }
            }

            for ((ia, ib) in adjacency) {
                val dx = nodes[ib].x - nodes[ia].x
                val dy = nodes[ib].y - nodes[ia].y
                val d = sqrt(dx * dx + dy * dy).takeIf { it > 1e-4f } ?: 1e-4f
                val f = spring * (d - rest)
                fx[ia] += (dx / d) * f
                fy[ia] += (dy / d) * f
                fx[ib] -= (dx / d) * f
                fy[ib] -= (dy / d) * f
            }

            // Folder cohesion: a note drifts towards the middle of its own folder.
            val sums = HashMap<String, Triple<Float, Float, Int>>()
            for (n in nodes) {
                if (n.group.isEmpty()) continue
                val c = sums[n.group] ?: Triple(0f, 0f, 0)
                sums[n.group] = Triple(c.first + n.x, c.second + n.y, c.third + 1)
            }
            for (i in nodes.indices) {
                if (nodes[i].fixed) continue
                val c = sums[nodes[i].group] ?: continue
                if (c.third < 2) continue
                fx[i] += (c.first / c.third - nodes[i].x) * groupPull
                fy[i] += (c.second / c.third - nodes[i].y) * groupPull
            }

            for (i in nodes.indices) {
                if (nodes[i].fixed) continue
                // A gentle pull to the centre keeps strays on the map.
                fx[i] += (0.5f - nodes[i].x) * 0.004f
                fy[i] += (0.5f - nodes[i].y) * 0.004f
                nodes[i].x = clamp01(nodes[i].x + fx[i].coerceIn(-0.03f, 0.03f) * cool)
                nodes[i].y = clamp01(nodes[i].y + fy[i].coerceIn(-0.03f, 0.03f) * cool)
            }
        }

        return nodes.associate { it.id to Point(it.x, it.y) }
    }
}
