/*
 * This file is part of Dis4IRC.
 *
 * Dis4IRC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dis4IRC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Dis4IRC.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.zachbr.dis4irc.bridge.mutator.mutators

import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.text.TextContentNodeRendererContext
import org.commonmark.renderer.text.TextContentRenderer
import java.util.*

// I haven't seen it be an issue but the back of my head says it could be, so remove dashes from this key
private val UNIQUE_KEY_STR = UUID.randomUUID().toString().replace("-", "")

/**
 * Translates Discord's markdown formatting to the IRC formatting codes and vice versa
 */
class TranslateFormatting : Mutator {
    private val markdownParser = Parser.Builder()
        .extensions(Collections.singletonList(StrikethroughExtension.create()))
        .build()

    private val ircMarkdownRenderer = TextContentRenderer.builder()
        .nodeRendererFactory { context -> IrcRenderer(context) }
        .build()

    override fun mutate(message: Message): Mutator.LifeCycle {
        message.contents = when (message.source.type) {
            PlatformType.IRC -> formatForDiscord(message.contents)
            PlatformType.DISCORD -> formatForIrc(message.contents)
        }

        return Mutator.LifeCycle.CONTINUE
    }

    /**
     * Takes a message from IRC and translates the formatting to Discord compatible rendering chars
     */
    private fun formatForDiscord(message: String): String {
        val stack = DiscordStack(message)
        return stack.toString()
    }

    /**
     * Takes a message from Discord and translates the formatting to IRC compatible rendering chars
     */
    private fun formatForIrc(message: String): String {
        // poor shrug man needs special handling to be spared the markdown parser
        val shrugMan = "¯\\_(ツ)_/¯"
        val shrugKey = UNIQUE_KEY_STR
        val out = message.replace(shrugMan, shrugKey)

        // render as markdown
        val parsed = markdownParser.parse(out)
        val rendered =  ircMarkdownRenderer.render(parsed)

        // put shrug man back
        return rendered.replace(shrugKey, shrugMan)
    }
}

/**
 * Custom renderer to take standard markdown (plus strikethrough) and emit IRC compatible formatting codes
 */
class IrcRenderer(context: TextContentNodeRendererContext) : AbstractVisitor(), NodeRenderer {
    private val textContent = context.writer

    override fun render(node: Node?) {
        node?.accept(this)
    }

    override fun getNodeTypes(): HashSet<Class<out Node>> {
        return HashSet(
            Arrays.asList(
                Document::class.java,
                Heading::class.java,
                Paragraph::class.java,
                BlockQuote::class.java,
                BulletList::class.java,
                FencedCodeBlock::class.java,
                HtmlBlock::class.java,
                ThematicBreak::class.java,
                IndentedCodeBlock::class.java,
                Link::class.java,
                ListItem::class.java,
                OrderedList::class.java,
                Image::class.java,
                Emphasis::class.java,
                StrongEmphasis::class.java,
                Text::class.java,
                Code::class.java,
                HtmlInline::class.java,
                SoftLineBreak::class.java,
                HardLineBreak::class.java
            )
        )
    }

    override fun visit(blockQuote: BlockQuote?) {
        visitChildren(blockQuote)
    }

    override fun visit(bulletList: BulletList?) {
        visitChildren(bulletList)
    }

    override fun visit(code: Code?) {
        textContent.write(IrcFormattingCodes.MONOSPACE.char)
        textContent.write(code?.literal)
        textContent.write(IrcFormattingCodes.MONOSPACE.char)
    }

    override fun visit(document: Document?) {
        visitChildren(document)
    }

    override fun visit(emphasis: Emphasis?) {
        textContent.write(IrcFormattingCodes.ITALICS.char)
        visitChildren(emphasis)
        textContent.write(IrcFormattingCodes.ITALICS.char)
    }

    override fun visit(fencedCodeBlock: FencedCodeBlock?) {
        textContent.write(IrcFormattingCodes.MONOSPACE.char)
        textContent.write(fencedCodeBlock?.literal)
        textContent.write(IrcFormattingCodes.MONOSPACE.char)
    }

    override fun visit(hardLineBreak: HardLineBreak?) {
        textContent.line()
    }

    override fun visit(heading: Heading?) {
        visitChildren(heading)
    }

    override fun visit(thematicBreak: ThematicBreak?) {
        visitChildren(thematicBreak)
    }

    override fun visit(htmlInline: HtmlInline?) {
        textContent.write(htmlInline?.literal)
    }

    override fun visit(htmlBlock: HtmlBlock?) {
        textContent.write(IrcFormattingCodes.MONOSPACE.char)
        textContent.write(htmlBlock?.literal)
        textContent.write(IrcFormattingCodes.MONOSPACE.char)
    }

    override fun visit(image: Image?) {
        textContent.write(image?.destination)
    }

    override fun visit(indentedCodeBlock: IndentedCodeBlock?) {
        textContent.write(IrcFormattingCodes.MONOSPACE.char)
        textContent.write(indentedCodeBlock?.literal)
        textContent.write(IrcFormattingCodes.MONOSPACE.char)
    }

    override fun visit(link: Link?) {
        textContent.write(link?.destination)
    }

    override fun visit(listItem: ListItem?) {
        visitChildren(listItem)
    }

    override fun visit(orderedList: OrderedList?) {
        visitChildren(orderedList)
    }

    override fun visit(paragraph: Paragraph?) {
        visitChildren(paragraph)
    }

    override fun visit(softLineBreak: SoftLineBreak?) {
        textContent.line()
    }

    override fun visit(strongEmphasis: StrongEmphasis?) {
        textContent.write(IrcFormattingCodes.BOLD.char)
        visitChildren(strongEmphasis)
        textContent.write(IrcFormattingCodes.BOLD.char)
    }

    override fun visit(text: Text?) {
        textContent.write(text?.literal)
    }

    override fun visit(customBlock: CustomBlock?) {
        TODO("not implemented")
    }

    override fun visit(customNode: CustomNode?) {
        if (customNode is Strikethrough) {
            textContent.write(IrcFormattingCodes.STRIKETHROUGH.char)
            visitChildren(customNode)
            textContent.write(IrcFormattingCodes.STRIKETHROUGH.char)
        } else {
            TODO("not implemented")
        }
    }

}

/**
 * General discord (markdown) formatting codes
 */
enum class DiscordFormattingCodes(val code: String) {
    BOLD("**"),
    ITALICS("*"),
    UNDERLINE("__"),
    STRIKETHROUGH("~~"),
    MONOSPACE_PARA("```"),
    MONOSPACE("`");

    override fun toString(): String = code
}

/**
 * Based on info from https://modern.ircdocs.horse/formatting.html
 */
enum class IrcFormattingCodes(val char: Char) {
    COLOR(0x03.toChar()),
    BOLD(0x02.toChar()),
    ITALICS(0x1D.toChar()),
    UNDERLINE(0x1F.toChar()),
    STRIKETHROUGH(0x1E.toChar()),
    MONOSPACE(0x11.toChar()),
    RESET(0x0F.toChar());

    val code: String = char.toString()
    override fun toString(): String = code
}

class DiscordStack(string: String) {
    private val builder = StringBuilder()
    private val stack = Stack<DiscordFormattingCodes>()
    private var isColor = false
    private var digits = 0

    init {
        string.toCharArray().forEach { character ->
            when(character) {
                IrcFormattingCodes.COLOR.char -> this.pushColor()
                IrcFormattingCodes.BOLD.char -> this.pushFormat(DiscordFormattingCodes.BOLD)
                IrcFormattingCodes.ITALICS.char -> this.pushFormat(DiscordFormattingCodes.ITALICS)
                IrcFormattingCodes.UNDERLINE.char -> this.pushFormat(DiscordFormattingCodes.UNDERLINE)
                IrcFormattingCodes.STRIKETHROUGH.char -> this.pushFormat(DiscordFormattingCodes.STRIKETHROUGH)
                IrcFormattingCodes.MONOSPACE.char -> this.pushMonospace()
                IrcFormattingCodes.RESET.char -> this.pushReset()
                else -> this.push(character)
            }
        }
    }

    private fun push(character: Char) {
        if (this.isColor) {
            if (character.isDigit()) {
                this.digits++
                if (this.digits >= 3) {
                    this.resetColor()
                } else {
                    return
                }
            } else if (this.digits > 0 && character == ',') {
                this.digits = 0
                return
            } else {
                this.resetColor()
            }
        }

        this.builder.append(character)
    }

    private fun pushColor() {
        this.isColor = true
    }

    private fun resetColor() {
        this.isColor = false
        this.digits = 0
    }

    private fun pushFormat(format: DiscordFormattingCodes) {
        val peekMatch = this.peekMatch(format)
        val contained = this.toggleFormat(format)
        val repush = contained && !peekMatch
        if (repush) {
            this.pushStack()
        }
        this.builder.append(format)
        if (repush) {
            this.pushStack()
        }
    }

    private fun peekMatch(format: DiscordFormattingCodes): Boolean {
        return !this.stack.isEmpty() && this.stack.peek() == format
    }

    private fun pushMonospace() {
        val contained = this.toggleFormat(DiscordFormattingCodes.MONOSPACE)
        if (!contained) {
            this.pushStack()
        } else {
            this.builder.append(DiscordFormattingCodes.MONOSPACE)
            this.pushStack()
        }
    }

    private fun toggleFormat(format: DiscordFormattingCodes): Boolean {
        val contained = this.stack.contains(format)
        if (contained) {
            this.stack.remove(format)
        } else {
            this.stack.push(format)
        }
        return contained
    }

    private fun pushStack() {
        this.stack.forEach { format -> this.builder.append(format) }
    }

    private fun pushReset() {
        while (!this.stack.isEmpty()) {
            this.builder.append(this.stack.pop())
        }
    }

    override fun toString(): String {
        this.pushReset() // reset is not required, simulate one to finish
        return this.builder.toString()
    }
}
