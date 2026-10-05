package com.petr.toll.probe

import com.petr.toll.classifier.NavTarget
import com.petr.toll.classifier.containsAny
import com.petr.toll.classifier.normalizeDesc

/** Pure matching behind [Navigator], kept Android-free for JVM tests. */
object NavMatch {
    fun isDmButton(entryName: String?, desc: String?, target: NavTarget): Boolean =
        containsAny(entryName, target.ids) ||
            (desc != null && target.descs.any { it.equals(normalizeDesc(desc), ignoreCase = true) })

    fun isSkipped(labels: List<String>, skipDescs: List<String>): Boolean =
        labels.any { label -> containsAny(label, skipDescs) }
}
