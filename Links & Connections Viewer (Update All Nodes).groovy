// ================= Save Details (عنوان حذف + آیکن - بدون خط) =================
def saveDetails(node, textLinks, connectors) {
    def html = []

    // Freeplane Links
    def freeplaneLinks = textLinks.findAll { 
        def u = it.uri ?: ""
        u.startsWith("freeplane:") || u.startsWith("#") || (u =~ /https?:\/\//)
    }
    if (freeplaneLinks && !freeplaneLinks.isEmpty()) {
        freeplaneLinks.each { l ->
            def titleNow = resolveTitleForLink(l)
            html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>🔗 " +
                    "<a data-link-type='text' href='${l.uri ?: ""}'>" +
                    HtmlUtils.toXMLEscapedText(titleNow) +
                    "</a></div>"
        }
    }

    // Obsidian Links
    def obsidianLinks = textLinks.findAll { 
        def u = it.uri ?: ""
        u.startsWith("obsidian://")
    }
    if (obsidianLinks && !obsidianLinks.isEmpty()) {
        obsidianLinks.each { l ->
            def titleNow = l.title ?: "Obsidian"
            html << "<div style='margin-right:0px;text-align:right;direction:rtl;'>📱 " +
                    "<a data-link-type='text' href='${l.uri ?: ""}'>" +
                    HtmlUtils.toXMLEscapedText(titleNow) +
                    "</a></div>"
        }
    }

    // Connectors
    def connectorsHTML = generateConnectorsHTML(connectors)
    if (connectorsHTML) {
        html << connectorsHTML
    }

    if (html && !html.isEmpty()) {
        node.details = "<html><body style='direction:rtl;'>${html.join("")}</body></html>"
        node.detailsContentType = "html"
    } else {
        node.details = null
        node.detailsContentType = null
    }
}
