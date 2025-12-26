// @ExecutionModes({ON_SINGLE_NODE="/menu_bar/links"})

import groovy.swing.SwingBuilder
import javax.swing.*
import java.awt.*
import java.awt.event.*
import javax.swing.SwingUtilities
import org.freeplane.features.mode.Controller
import org.freeplane.features.map.NodeModel
import javax.swing.AbstractAction
import javax.swing.KeyStroke

def inCons = node.connectorsIn
def outCons = node.connectorsOut

def connectionTypes = [:]
def nodeMap = [:]

// تعیین نوع اتصال برای هر گره
outCons.each { connector ->
    def targetNode = connector.target.delegate
    connectionTypes[targetNode] = connectionTypes.getOrDefault(targetNode, '') + 'خروج'
    nodeMap[targetNode] = connector
}
inCons.each { connector ->
    def sourceNode = connector.source.delegate
    connectionTypes[sourceNode] = connectionTypes.getOrDefault(sourceNode, '') + 'ورود'
    nodeMap[sourceNode] = connector
}

// دسته‌بندی گره‌ها بر اساس نوع اتصال
def groupEnter = []
def groupExit = []
def groupEnterExit = []

connectionTypes.keySet().each { n ->
    def type = connectionTypes[n]
    if (type.contains('ورود') && type.contains('خروج')) {
        groupEnterExit.add(n)
    } else if (type.contains('ورود')) {
        groupEnter.add(n)
    } else if (type.contains('خروج')) {
        groupExit.add(n)
    }
}

// ایجاد مدل لیست و اضافه کردن گره‌ها به ترتیب ورود، خروج، دوطرفه
def listModel = new DefaultListModel()
[groupEnter, groupExit, groupEnterExit].each { group ->
    group.each { listModel.addElement(it) }
}

// اگر گره‌ای پیدا نشد، نمایش پیام
if (listModel.isEmpty()) {
    JOptionPane.showMessageDialog(
        Controller.currentController.mapViewManager.mapView.parent.parent,
        "هیچ گره متصل یافت نشد.",
        "اطلاع",
        JOptionPane.INFORMATION_MESSAGE)
    return
}

def swing = new SwingBuilder()
swing.edt {
    def jl = new JList(listModel)
    jl.selectionMode = ListSelectionModel.SINGLE_SELECTION
    jl.selectedIndex = 0

    // رندر چندخطی با متن راست‌چین و خط جداکننده در پایین هر گره
    jl.cellRenderer = { JList list, Object value, int index, boolean isSelected, boolean cellHasFocus ->
        def type = connectionTypes[value]
        def prefix = ''
        if (type.contains('ورود') && type.contains('خروج')) prefix = 'دوطرفه: '
        else if (type.contains('خروج')) prefix = 'خروج: '
        else if (type.contains('ورود')) prefix = 'ورود: '

        def plainText = value.text
        // فقط خط اول متن را نمایش بده
        def firstLine = plainText.contains('\n') ? plainText.substring(0, plainText.indexOf('\n')) : plainText

        // اضافه کردن خط جداکننده "......................................................"
        def fullText = prefix + firstLine + "\n" + "......................................................"

        JTextArea area = new JTextArea(fullText)
        area.lineWrap = true
        area.wrapStyleWord = true
        area.editable = false

        // راست‌چین کردن متن
        area.componentOrientation = ComponentOrientation.RIGHT_TO_LEFT

        area.background = isSelected ? list.selectionBackground : list.background
        area.foreground = isSelected ? list.selectionForeground : list.foreground
        area.font = list.font
        area.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        area.opaque = true
        return area
    } as ListCellRenderer

    // فعال کردن ارتفاع پویا برای سلول‌ها
    jl.fixedCellHeight = -1

    def myScrollPane
    def myFrame = swing.frame(
        title: 'گره‌های متصل',
        size: [800, 1000],
        locationRelativeTo: Controller.currentController.getMapViewManager().getMapViewComponent(),
        defaultCloseOperation: WindowConstants.DISPOSE_ON_CLOSE,
        alwaysOnTop: true
    ) {
        borderLayout()
        myScrollPane = scrollPane(constraints: BorderLayout.CENTER) {
            widget jl
        }
    }

    myFrame.show()

    SwingUtilities.invokeLater {
        def hBar = myScrollPane.horizontalScrollBar
        hBar.value = hBar.maximum
    }

    jl.addMouseListener(new MouseAdapter() {
        @Override
        void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 1) {
                int index = jl.locationToIndex(e.getPoint())
                if (index >= 0) {
                    def selectedNode = jl.model.getElementAt(index)
                    Controller.currentController.mapViewManager.mapView.getMapSelection().selectAsTheOnlyOneSelected(selectedNode)
                    myFrame.dispose()
                }
            }
        }
    })

    // حذف کانکتور با کلید DELETE
    jl.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "deleteConnector")
    jl.getActionMap().put("deleteConnector", new AbstractAction() {
        @Override
        void actionPerformed(ActionEvent e) {
            def selectedNode = jl.selectedValue
            if (selectedNode != null) {
                def connectorToRemove = nodeMap[selectedNode]
                if (connectorToRemove != null) {
                    try {
                        node.removeConnector(connectorToRemove)
                        JOptionPane.showMessageDialog(myFrame, "کانکتور با موفقیت حذف شد.", "موفق", JOptionPane.INFORMATION_MESSAGE)
                        myFrame.dispose()
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(myFrame, "خطا در حذف کانکتور: " + ex.message, "خطا", JOptionPane.ERROR_MESSAGE)
                    }
                }
            }
        }
    })

    // انتخاب گره با کلید ENTER
    jl.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "selectNode")
    jl.getActionMap().put("selectNode", new AbstractAction() {
        @Override
        void actionPerformed(ActionEvent e) {
            def selectedNode = jl.selectedValue
            if (selectedNode != null) {
                Controller.currentController.mapViewManager.mapView.getMapSelection().selectAsTheOnlyOneSelected(selectedNode)
                myFrame.dispose()
            }
        }
    })

    // حرکت انتخاب به بالا با کلید UP
    jl.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("UP"), "moveUp")
    jl.getActionMap().put("moveUp", new AbstractAction() {
        @Override
        void actionPerformed(ActionEvent e) {
            def i = jl.selectedIndex
            if (i > 0) jl.selectedIndex = i - 1
        }
    })

    // حرکت انتخاب به پایین با کلید DOWN
    jl.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DOWN"), "moveDown")
    jl.getActionMap().put("moveDown", new AbstractAction() {
        @Override
        void actionPerformed(ActionEvent e) {
            def i = jl.selectedIndex
            if (i < listModel.size() - 1) jl.selectedIndex = i + 1
        }
    })

    myFrame.addWindowFocusListener(new WindowAdapter() {
        @Override
        void windowLostFocus(WindowEvent e) {
            if (myFrame.isDisplayable()) {
                myFrame.dispose()
            }
        }
    })
}
