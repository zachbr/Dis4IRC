/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.mutator.mutators

import io.zachbr.dis4irc.bridge.message.CommandMessage
import io.zachbr.dis4irc.bridge.message.DiscordMessage
import io.zachbr.dis4irc.bridge.message.DiscordSource
import io.zachbr.dis4irc.bridge.message.IrcMessage
import io.zachbr.dis4irc.bridge.message.IrcSource
import io.zachbr.dis4irc.bridge.message.PlatformMessage
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import io.zachbr.dis4irc.util.DiscordSpoiler
import io.zachbr.dis4irc.util.DiscordSpoilerExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.text.TextContentNodeRendererContext
import org.commonmark.renderer.text.TextContentRenderer
import java.util.*

private const val MINIMUM_DISCORD_MESSAGE_LENGTH = 3
// I haven't seen it be an issue but the back of my head says it could be, so remove dashes from this key
private val UNIQUE_KEY_STR = UUID.randomUUID().toString().replace("-", "")

/**
 * Translates Discord's markdown formatting to the IRC formatting codes and vice versa
 */
class TranslateFormatting : Mutator {
    private val markdownParser = Parser.Builder()
        .extensions(listOf(StrikethroughExtension.create(), DiscordSpoilerExtension.create()))
        .build()

    private val ircMarkdownRenderer = TextContentRenderer.builder()
        .nodeRendererFactory { context -> IrcRenderer(context) }
        .build()

    override fun mutate(message: PlatformMessage): Mutator.LifeCycle {
        when (message) {
            is CommandMessage -> {} // nothing to do here
            is IrcMessage -> message.contents = formatForDiscord(message.contents)
            is DiscordMessage -> {
                message.contents = formatForIrc(message.contents)

                for (embed in message.embeds) {
                    embed.string = embed.string?.let { formatForIrc(it) }
                }
            }
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
        // no-op short messages that the markdown parser would incorrectly interpret
        // as part of a larger text section
        if (message.length < MINIMUM_DISCORD_MESSAGE_LENGTH) {
            return message
        }

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
    private val spoilerFormatCodeSequence = "${IrcFormattingCodes.COLOR}${IrcColorCodes.BLACK},${IrcColorCodes.BLACK}"

    override fun render(node: Node?) {
        node?.accept(this)
    }

    override fun getNodeTypes(): HashSet<Class<out Node>> {
        return HashSet(
            listOf (
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
        textContent.write("> ") // don't strip this off as part of parse, block quotes aren't a thing in discord
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
        // discord doesn't do anything fancy for lists, neither should we, present them as they are
        textContent.write("- ")

        visitChildren(listItem)

        // spaces between list items
        if (listItem?.parent?.lastChild != listItem) {
            textContent.write(" ")
        }
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
        val wrapper: Char = when (strongEmphasis?.openingDelimiter) {
            DiscordFormattingCodes.BOLD.code -> IrcFormattingCodes.BOLD.char
            DiscordFormattingCodes.UNDERLINE.code -> IrcFormattingCodes.UNDERLINE.char
            else -> throw IllegalArgumentException("Unknown strong emphasis delimiter: ${strongEmphasis?.openingDelimiter}")
        }
        textContent.write(wrapper)
        visitChildren(strongEmphasis)
        textContent.write(wrapper)
    }

    override fun visit(text: Text?) {
        textContent.write(text?.literal)
    }

    override fun visit(customNode: CustomNode?) {
        when (customNode) {
            is Strikethrough -> {
                textContent.write(IrcFormattingCodes.STRIKETHROUGH.char)
                visitChildren(customNode)
                textContent.write(IrcFormattingCodes.STRIKETHROUGH.char)
            }
            is DiscordSpoiler -> {
                textContent.write(spoilerFormatCodeSequence)
                visitChildren(customNode)
                textContent.write(IrcFormattingCodes.COLOR.char)
            }
            else -> {
                throw IllegalArgumentException("Unknown custom node: $customNode")
            }
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
 * Based on info from https://modern.ircdocs.horse/formatting.html#characters
 */
enum class IrcFormattingCodes(val char: Char) {
    COLOR(0x03.toChar()),
    BOLD(0x02.toChar()),
    ITALICS(0x1D.toChar()),
    UNDERLINE(0x1F.toChar()),
    STRIKETHROUGH(0x1E.toChar()),
    MONOSPACE(0x11.toChar()),
    RESET(0x0F.toChar());

    private val code: String = char.toString()
    override fun toString(): String = code
}

/**
 * Based on info from https://modern.ircdocs.horse/formatting.html#colors
 */
enum class IrcColorCodes(private val code: String) { // always use 2 digit codes
    WHITE("00"),
    BLACK("01"),
    DEFAULT("99"); // this is not well supported by clients

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
