package cuongdev.app.smartview.printer.utils

object Utils {
     fun wrap(str: String, charsPerLine: Int = 20): List<String> {
        if (str.length < charsPerLine) return listOf(str)

        val words = str.split(" ")
        val lengths = mutableListOf<Int>()

        for (i in words.indices) {
            lengths += words[i].replace("/<.+>/g".toRegex(), "")
                .replace("/&.+;/g".toRegex(), "").length
        }

        var line = mutableListOf<String>()
        var offset = 0
        val out = mutableListOf<String>()

        var i = 0
        while (i <= words.lastIndex) {
            if (offset + (lengths[i] + line.size - 1) < charsPerLine) {
                line.add(words[i])
                offset += lengths[i]
            } else {
                out.add(line.joinToString(" "))
                offset = 0
                line = mutableListOf()
                i -= 1
            }
            if (i == words.size - 1)
                out.add(line.joinToString(" "))

            i++
        }
        return out
    }
}