/*
 * Copyright (c) 2008-2015 Emmanuel Dupuy
 * This program is made available under the terms of the GPLv3 License.
 */

package jd.gui.view.component

import groovy.transform.CompileStatic
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaUI
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.folding.Fold
import org.fife.ui.rtextarea.Gutter
import org.fife.ui.rtextarea.LineNumberList
import org.fife.ui.rtextarea.RTextArea
import org.fife.ui.rtextarea.RTextAreaUI

import javax.swing.JComponent
import javax.swing.text.EditorKit
import javax.swing.text.JTextComponent
import java.awt.Color
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle

@CompileStatic
abstract class SourcePage extends HyperlinkPage {

    Color errorForeground = Color.RED
    boolean showMisalignment = true

    void setErrorForeground(Color color) {
        errorForeground = color
    }

    void setShowMisalignment(boolean b) {
        showMisalignment = b
    }

    /**
     * Map[textarea line number] = source line number
     */
    protected int[] lineNumberMap = null

    void setMaxLineNumber(int maxLineNumber) {
        if (maxLineNumber > 0) {
            if (lineNumberMap == null) {
                lineNumberMap = new int[maxLineNumber * 3 / 2]
            } else if (lineNumberMap.length <= maxLineNumber) {
                int[] tmp = new int[maxLineNumber * 3 / 2]
                System.arraycopy(lineNumberMap, 0, tmp, 0, lineNumberMap.length)
                lineNumberMap = tmp
            }
        }
    }

    void setLineNumber(int textAreaLineNumber, int sourceLineNumber) {
        if (sourceLineNumber > 0) {
            setMaxLineNumber(textAreaLineNumber)
            lineNumberMap[textAreaLineNumber] = sourceLineNumber
        }
    }

    void clearLineNumbers() {
        if (lineNumberMap) {
            Arrays.fill(lineNumberMap, 0)
        }
    }

    int getMaximumSourceLineNumber() {
        int max = 0
        for (int ln : lineNumberMap) {
            if (max < ln) {
                max = ln
            }
        }
        return max
    }

    @CompileStatic
    int getTextAreaLineNumber(int sourceLineNumber) {
        int textAreaLineNumber = 1
        int greatestLowerSourceLineNumber = 0
        int i = lineNumberMap.length

        while (i-- > 0) {
            int sln = lineNumberMap[i]
            if (sln <= sourceLineNumber) {
                if (greatestLowerSourceLineNumber < sln) {
                    greatestLowerSourceLineNumber = sln
                    textAreaLineNumber = i
                }
            }
        }

        return textAreaLineNumber
    }

    protected RSyntaxTextArea newRSyntaxTextArea() { new SourceSyntaxTextArea() }

    @CompileStatic
    class SourceSyntaxTextArea extends RSyntaxTextArea {
        /**
         * @see HyperlinkPage.HyperlinkSyntaxTextArea#getUnderlineForToken(org.fife.ui.rsyntaxtextarea.Token)
         */
        boolean getUnderlineForToken(Token t) {
            def entry = hyperlinks.floorEntry(t.offset)
            if (entry) {
                def data = entry.value
                if (data && (t.offset < data.endPosition) && (t.offset >= data.startPosition) && isHyperlinkEnabled(data)) {
                    return true
                }
            }
            return super.getUnderlineForToken(t)
        }

        protected RTextAreaUI createRTextAreaUI() { new SourceSyntaxTextAreaUI(this) }
    }

    /**
     * A lot of code to replace the default LineNumberList...
     */
    @CompileStatic
    class SourceSyntaxTextAreaUI extends RSyntaxTextAreaUI {
        SourceSyntaxTextAreaUI(JComponent rSyntaxTextArea) { super(rSyntaxTextArea) }
        EditorKit getEditorKit(JTextComponent tc) { new SourceSyntaxTextAreaEditorKit() }
        Rectangle getVisibleEditorRect() { super.getVisibleEditorRect() }
    }

    @CompileStatic
    class SourceSyntaxTextAreaEditorKit extends RSyntaxTextAreaEditorKit {
        LineNumberList createLineNumberList(RTextArea textArea) { new SourceLineNumberList(textArea) }
    }

    /**
     * Why 'LineNumberList' is so unexpandable ? Too many private fields & methods and too many package scope.
     */
    @CompileStatic
    class SourceLineNumberList extends LineNumberList {
        protected RTextArea rTextArea
        protected Map<?,?> aaHints
        protected Rectangle visibleRect
        protected Insets textAreaInsets

        SourceLineNumberList(RTextArea textArea) {
            super(textArea, null)
            this.rTextArea = textArea
        }

        @Override
        protected void init() {
            super.init()
            visibleRect = new Rectangle()
            aaHints = RSyntaxUtilities.getDesktopAntiAliasHints();
            textAreaInsets = null
        }

        /**
         * @see org.fife.ui.rtextarea.LineNumberList#paintComponent(java.awt.Graphics)
         */
        @Override
        protected void paintComponent(Graphics g) {
            visibleRect = g.getClipBounds(visibleRect)

            if (visibleRect == null) {
                visibleRect = getVisibleRect()
            }
            if (visibleRect == null) {
                return
            }

            int cellWidth = getPreferredSize().@width
            int cellHeight = rTextArea.lineHeight
            int ascent = rTextArea.maxAscent
            def fm = ((RSyntaxTextArea)rTextArea).foldManager
            int RHS_BORDER_WIDTH = rhsBorderWidth
            def metrics = g.fontMetrics
            int rhs = width - RHS_BORDER_WIDTH

            if (parent instanceof Gutter) { // Should always be true
                g.setColor(parent.background)
            } else {
                g.setColor(background)
            }

            g.fillRect(0, visibleRect.@y, cellWidth, visibleRect.@height)
            g.setFont(font)

            if (aaHints) {
                ((Graphics2D)g).addRenderingHints(aaHints)
            }

            if (rTextArea.lineWrap) {
                def ui = (SourceSyntaxTextAreaUI)rTextArea.getUI()
                def v = ui.getRootView(rTextArea).getView(0)
                def root = rTextArea.document.defaultRootElement
                int lineCount = root.elementCount
                int topPosition = rTextArea.viewToModel(visibleRect.location)
                int topLine = root.getElementIndex(topPosition)
                def visibleEditorRect = ui.visibleEditorRect
                def r = LineNumberList.getChildViewBounds(v, topLine, visibleEditorRect)
                int y = r.@y

                int visibleBottom =  visibleRect.@y + visibleRect.@height

                // Keep painting lines until our y-coordinate is past the visible
                // end of the text area.

                while (y < visibleBottom) {
                    r = getChildViewBounds(v, topLine, visibleEditorRect)

                    // Paint the line number.
                    paintLineNumber(g, metrics, rhs, y+ascent, topLine + 1)

                    // The next possible y-coordinate is just after the last line
                    // painted.
                    y += r.@height

                    // Update topLine (we're actually using it for our "current line"
                    // variable now).
                    if (fm) {
                        Fold fold = fm.getFoldForLine(topLine)
                        if (fold?.isCollapsed()) {
                            topLine += fold.collapsedLineCount
                        }
                    }

                    if (++topLine >= lineCount) {
                        break
                    }
                }
            } else {
                textAreaInsets = rTextArea.getInsets(textAreaInsets)

                if (visibleRect.@y < textAreaInsets.@top) {
                    visibleRect.@height -= (textAreaInsets.@top - visibleRect.@y)
                    visibleRect.@y = textAreaInsets.@top
                }

                int topLine = (int) (visibleRect.@y - textAreaInsets.@top) / cellHeight
                int actualTopY = topLine * cellHeight + textAreaInsets.top
                int y = actualTopY + ascent

                // Get the actual first line to paint, taking into account folding.
                topLine += fm.getHiddenLineCountAbove(topLine, true)

                // Paint line numbers
                g.setColor(foreground)

                int line = topLine + 1

                while ((y < visibleRect.@y + visibleRect.@height + ascent) && (line <= rTextArea.lineCount)) {
                    paintLineNumber(g, metrics, rhs, y, line)

                    y += cellHeight

                    if (fm != null) {
                        Fold fold = fm.getFoldForLine(line - 1)
                        // Skip to next line to paint, taking extra care for lines with
                        // block ends and begins together, e.g. "} else {"
                        while (fold?.isCollapsed()) {
                            int hiddenLineCount = fold.lineCount
                            if (hiddenLineCount == 0) {
                                // Fold parser identified a 0-line fold region... This
                                // is really a bug, but we'll handle it gracefully.
                                break
                            }
                            line += hiddenLineCount
                            fold = fm.getFoldForLine(line - 1)
                        }
                    }

                    line++
                }
            }
        }

        protected void paintLineNumber(Graphics g, FontMetrics metrics, int x, int y, int lineNumber) {
            int sourceLineNumber

            if (lineNumberMap) {
                sourceLineNumber = (lineNumber < lineNumberMap.length) ? lineNumberMap[lineNumber] : 0
            } else {
                sourceLineNumber = lineNumber
            }

            if (sourceLineNumber != 0) {
                String number = Integer.toString(sourceLineNumber)
                int strWidth = metrics.stringWidth(number)
                g.setColor(showMisalignment && (lineNumber != sourceLineNumber) ? errorForeground : foreground)
                g.drawString(number, x-strWidth, y)
            }
        }

        int getRhsBorderWidth() { ((RSyntaxTextArea)rTextArea).isCodeFoldingEnabled() ? 0 : 4 }
    }
}
