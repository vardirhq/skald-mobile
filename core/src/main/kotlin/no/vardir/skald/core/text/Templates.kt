package no.vardir.skald.core.text

/** Deliberately small, predictable schema-template expansion shared by clients. */
object Templates {
    fun render(template: String, title: String, date: String): String = template
        .replace("{{title}}", title)
        .replace("{{date}}", date)
}
