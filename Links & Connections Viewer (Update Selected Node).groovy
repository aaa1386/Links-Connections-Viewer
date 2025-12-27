// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/link"})
// aj1386

import org.freeplane.core.util.HtmlUtils
import javax.swing.*


// ================= بررسی وجود URI =================
def hasURI(node) {
    extractPlainTextFromNode(node).split('\n').any { it.trim().startsWith("freeplane:") }
}


// ================= دیالوگ =================
def showSimpleDialog() {
    Object[] options = ["یک طرفه", "دو طرفه"]
    JOptionPane.showInputDialog(
        ui.frame,
        "لطفا نوع لینک‌سازی را انتخاب کنید:",
        "انتخاب نوع لینک",
        JOptionPane.QUESTION_MESSAGE,
        null,
        options,
        options[0]
    )
}


// ================= متن خام =================
def extractPlainTextFromNode(node) {
    def c = node.text ?: ""
    if (c.contains("<body>")) {
        def s = c.indexOf("<body>") + 6
        def e = c.indexOf("</body>")
        if (s > 5 && e > s) {
            return c.substring(s, e)
                    .replaceAll("<[^>]+>", "\n")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("\n+", "\n")
                    .trim()
        }
    }
    c
}

def getFirstLineFromText(text) {
    if (!text) return "لینک"
    text.split('\n').find { it.trim() && !it.startsWith("freeplane:") && !it.startsWith("obsidian://") }?.trim() ?: "لینک"
}


// ================= تبدیل NodeModel → NodeProxy =================
def asProxy(n) {
    (n.metaClass.hasProperty(n, "connectorsIn")) ? n :
        c.find { it.delegate == n }.find()
}


// ================= استخراج کانکتورها (بر اساس جهت فلش) =================
def extractConnectedNodes(node) {
    node = asProxy(node)
    if (!node) return ['ورودی': [], 'خروجی': [], 'دوطرفه': []]

    def nodeId = node.id
    def grouped = ['ورودی': [], 'خروجی': [], 'دوطرفه': []]

    // همه کانکتورها از in و out
    def allConnectors = (node.connectorsIn + node.connectorsOut).unique()

    allConnectors.each { con ->
        def src = con.source?.delegate
        def tgt = con.target?.delegate
        if (!src || !tgt) return

        def srcId = src.id
        def tgtId = tgt.id

        def otherNode
        def nodeIsSource = false

        // تشخیص اینکه گره جاری source است یا target
        if (srcId == nodeId) {
            otherNode   = tgt
            nodeIsSource = true
        } else if (tgtId == nodeId) {
            otherNode   = src
        } else {
            // کانکتوری که اصلاً به این گره ربطی ندارد
            return
        }

        if (!otherNode) return

        def start = con.hasStartArrow()
        def end   = con.hasEndArrow()

        if (start && end) {
            // دو فلش → دوطرفه
            if (!grouped['دوطرفه'].contains(otherNode))
                grouped['دوطرفه'] << otherNode
        }
        else if (start && !end) {
            // معکوس: start=true end=false → برعکس
            if (nodeIsSource) {
                // node → otherNode → «ورودی» نسبت به node (طبق الگوی تو)
                if (!grouped['ورودی'].contains(otherNode))
                    grouped['وریدی'] << otherNode
            } else {
                // otherNode → node → «خروجی»
                if (!grouped['خروجی'].contains(otherNode))
                    grouped['خروجی'] << otherNode
            }
        }
        else if (!start && end) {
            // معکوس: !start end=true → برعکس
            if (nodeIsSource) {
                // otherNode → node → «خروجی»
                if (!grouped['خروجی'].contains(otherNode))
                    grouped['خروجی'] << otherNode
            } else {
                // node → otherNode → «ورودی»
                if (!grouped['ورودی'].contains(otherNode))
                    grouped['ورودی'] << otherNode
            }
        }
        else {
            // بدون فلش → بر اساس جهت connector (source / target)
            if (nodeIsSource) {
                grouped['خروجی'] << otherNode
            } else {
                grouped['ورودی'] << otherNode
            }
        }
    }

    grouped
}


// ================= HTML کانکتورها =================
def generateConnectorsHTML(grouped) {
    def html = []

    def makeLink = { n ->
        "<a data-link-type='connector' href='#${n.id}'>" +
        HtmlUtils.toXMLEscapedText(getFirstLineFromText(extractPlainTextFromNode(n))) +
        "</a>"
    }

    ['ورودی','خروجی','دوطرفه'].each { type ->
        def nodes = grouped[type]
        if (nodes && !nodes.isEmpty()) {

            // برچسب‌های جدید برای عنوان هر بخش
            def titleLabel =
                (type == 'ورودی')   ? '↙️ورودی (Input):' :
                (type == 'خروجی')   ? '↗️خروجی (Output):' :
                                      '↔️دوطرفه (Mutual):'

            html << "<div style='font-weight:bold;margin:5px 0;text-align:right;direction:rtl;'>${titleLabel}</div>"
            nodes.each { n ->
                // بالت کاملاً کنار لبه راست
                html << "<div style='margin-right:0px;margin-bottom:3px;text-align:right;direction:rtl;'>• ${makeLink(n)}</div>"
            }
        }
    }
    html.join("")
}


// ================= لینک‌های متنی =================
def extractTextLinksFromDetails(node) {
    def list = []
    def h = node.detailsText
    if (!h || !h.contains("<body>")) return list
    def body = h.substring(h.indexOf("<body>")+6, h.indexOf("</body>"))
    def m = body =~ /<a\s+data-link-type="text"[^>]*href="([^"]+)"[^>]*>([^<]+)<\/a>/
    m.each { list << [uri: it[1], title: it[2]] }
    list
}


// ================= استخراج لینک‌ها از متن گره (یو آر آی حذف می‌شود) =================
def extractTextLinksFromNodeText(node) {
    def freeplaneLinks = []
    def obsidianLinks = []
    def keepLines = []

    extractPlainTextFromNode(node).split('\n').each { l ->
        def t = l.trim()
        if (t.startsWith("freeplane:")) {
            def parts = t.split(' ', 2)
            def uri = parts[0]
            def title = null

            if (uri.contains("#")) {
                def targetId = uri.substring(uri.lastIndexOf('#')+1)
                def targetNode = c.find { it.id == targetId }.find()
                if (targetNode) {
                    title = getFirstLineFromText(extractPlainTextFromNode(targetNode))
                } else {
                    title = (parts.length > 1) ? parts[1].trim() : "عنوان را از نقشه دیگر جایگزین کن"
                }
            } else {
                title = (parts.length > 1) ? parts[1].trim() : "لینک"
            }

            freeplaneLinks << [uri: uri, title: title]
        } 
        // ✅ Obsidian URI
        else if (t.startsWith("obsidian://")) {
            def parts = t.split(' ', 2)
            def uri = parts[0]
            def title = (parts.length > 1) ? parts[1].trim() : "ابسیدین"
            obsidianLinks << [uri: uri, title: title]
        }
        else if (t) {
            keepLines << t
        }
    }
    node.text = keepLines.join("\n")
    freeplaneLinks + obsidianLinks
}


// ================= ذخیره Details =================
def saveDetails(node, textLinks, connectors) {
    def html = []
    def hasNewCategory = false
    
    // ✅ گروه‌بندی Freeplane
    def freeplaneLinks = textLinks.findAll { it.uri.startsWith("freeplane:") || it.uri.startsWith("#") }
    if (freeplaneLinks && !freeplaneLinks.isEmpty()) {
        // عنوان جدید فریپلن
        html << "<div style='font-weight:bold;margin:5px 0;text-align:right;direction:rtl;'>🔗 فریپلن(FP):</div>"
        freeplaneLinks.each { l ->
            html << "<div style='margin-right:0px;text-align:right;'>• " +
                    "<a data-link-type='text' href='${l.uri}'>" +
                    HtmlUtils.toXMLEscapedText(l.title) +
                    "</a></div>"
        }
        hasNewCategory = true
    }
    
    // ✅ گروه‌بندی Obsidian
    def obsidianLinks = textLinks.findAll { it.uri.startsWith("obsidian://") }
    if (obsidianLinks && !obsidianLinks.isEmpty()) {
        if (hasNewCategory) {
            html << "<hr>"  // خط قبل دسته جدید
        }
        // عنوان جدید ابسیدین
        html << "<div style='font-weight:bold;margin:5px 0;text-align:right;direction:rtl;'>📱 ابسیدین(Obsidian):</div>"
        obsidianLinks.each { l ->
            html << "<div style='margin-right:0px;text-align:right;'>• " +
                    "<a data-link-type='text' href='${l.uri}'>" +
                    HtmlUtils.toXMLEscapedText(l.title) +
                    "</a></div>"
        }
        hasNewCategory = true
    }
    
    def connectorsHTML = generateConnectorsHTML(connectors)
    if (connectorsHTML) {
        if (hasNewCategory) {
            html << "<hr>"  // خط قبل کانکتورها
        }
        html << connectorsHTML
    }
    
    // 🔹 فقط اگر محتوا هست set کن
    if (html && !html.isEmpty()) {
        node.details = "<html><body style='direction:rtl;'>${html.join("")}</body></html>"
        node.detailsContentType = "html"
    } else {
        node.details = null
        node.detailsContentType = null
    }
}


// ================= لینک برگشتی متنی =================
def createBackwardTextLink(targetNode, sourceNode) {
    def sourceUri = "#${sourceNode.id}"
    def sourceTitle = getFirstLineFromText(extractPlainTextFromNode(sourceNode))

    def textLinks = extractTextLinksFromDetails(targetNode)
    if (textLinks.any { it.uri == sourceUri }) return

    textLinks << [uri: sourceUri, title: sourceTitle]
    saveDetails(targetNode, textLinks, extractConnectedNodes(targetNode))
}


// ================= آپدیت کانکتورهای طرف مقابل =================
def updateOtherSideConnectors(centerNode) {
    def connected = extractConnectedNodes(centerNode)
    connected.values().flatten().unique().each { other ->
        def proxy = asProxy(other)
        if (!proxy) return
        saveDetails(
            proxy,
            extractTextLinksFromDetails(proxy),
            extractConnectedNodes(proxy)
        )
    }
}


// ================= پردازش گره =================
def processNode(mode) {
    def node = c.selected
    if (!node) return

    def newLinks = extractTextLinksFromNodeText(node)
    def connectors = extractConnectedNodes(node)
    def existingTextLinks = extractTextLinksFromDetails(node)
    def finalTextLinks = (existingTextLinks + newLinks).unique { it.uri }

    saveDetails(node, finalTextLinks, connectors)

    // دوطرفه → لینک برگشتی
    if (mode == "دو طرفه") {
        newLinks.each { link ->
            if (link.uri.contains("#")) {
                def targetId = link.uri.substring(link.uri.lastIndexOf('#') + 1)
                def targetNode = c.find { it.id == targetId }.find()
                if (targetNode && targetNode != node) {
                    createBackwardTextLink(targetNode, node)
                }
            }
        }
    }

    // آپدیت کانکتورهای طرف مقابل
    updateOtherSideConnectors(node)
}


// ================= اجرا =================
try {
    def node = c.selected
    if (!node || !hasURI(node)) {
        // اگر URI ندارد → مستقیم اجرا با حالت یک طرفه
        processNode("یک طرفه")
    } else {
        // اگر URI دارد → نمایش دیالوگ
        def mode = showSimpleDialog()
        if (mode) {
            processNode(mode)
        }
    }
} catch (e) {
    ui.showMessage("خطا:\n${e.message}", 0)
}
