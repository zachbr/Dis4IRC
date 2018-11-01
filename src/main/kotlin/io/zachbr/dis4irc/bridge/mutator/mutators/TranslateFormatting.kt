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
import io.zachbr.dis4irc.bridge.message.Source
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import io.zachbr.dis4irc.util.countSubstring
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.text.TextContentNodeRendererContext
import org.commonmark.renderer.text.TextContentRenderer
import java.util.*

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
            Source.Type.IRC -> formatForDiscord(message.contents)
            Source.Type.DISCORD -> formatForIrc(message.contents)
        }

        return Mutator.LifeCycle.CONTINUE
    }

    /**
     * Takes a message from IRC and translates the formatting to Discord compatible rendering chars
     */
    private fun formatForDiscord(message: String): String {
        var out = fixFormattingBalance(message, IrcFormattingCodes.values())

        out = out.replace(IrcFormattingCodes.BOLD.code, DiscordFormattingCodes.BOLD.code)
        out = out.replace(IrcFormattingCodes.ITALICS.code, DiscordFormattingCodes.ITALICS.code)
        out = out.replace(IrcFormattingCodes.UNDERLINE.code, DiscordFormattingCodes.UNDERLINE.code)
        out = out.replace(IrcFormattingCodes.STRIKETHROUGH.code, DiscordFormattingCodes.STRIKETHROUGH.code)
        out = out.replace(IrcFormattingCodes.MONOSPACE.code, DiscordFormattingCodes.MONOSPACE.code)

        return out
    }

    /**
     * Ensures that IRC formatting chars are balanced, that is even, as there is no requirement
     * for them to be.
     */
    private fun <T : Enum<T>> fixFormattingBalance(message: String, values: Array<T>): String {
        var out = message

        for (formattingCode in values) {
            if (countSubstring(out, formattingCode.toString()) % 2 != 0) {
                out += formattingCode.toString()
            }
        }

        return out
    }

    /**
     * Takes a message from Discord and translates the formatting to IRC compatible rendering chars
     */
    private fun formatForIrc(message: String): String {
        var out = fixFormattingBalance(message, DiscordFormattingCodes.values()) // required for markdown parsing

        // poor shrug man needs special handling to be spared the markdown parser
        val shrugMan = "¯\\_(ツ)_/¯"
        val shrugKey = UUID.randomUUID().toString()
        out = out.replace(shrugMan, shrugKey)

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
    BOLD(0x02.toChar()),
    ITALICS(0x1D.toChar()),
    UNDERLINE(0x1F.toChar()),
    STRIKETHROUGH(0x1E.toChar()),
    MONOSPACE(0x11.toChar()),
    RESET(0x0F.toChar());

    val code: String = char.toString()
    override fun toString(): String = code
}
