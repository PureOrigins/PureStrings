package it.pureorigins.purestrings

import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.papermc.paper.adventure.AdventureComponent
import it.pureorigins.common.*
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.network.protocol.game.ClientboundChatPacket
import org.bukkit.plugin.java.JavaPlugin
import java.util.*


class PureStrings : JavaPlugin() {
    override fun onEnable() {
        val strings = json.readFileAs<Map<String, String>>(file("strings.json"), HashMap())
        registerPacketHandler(OverrideStringsPacketHandler(strings))
    }
}

@ChannelHandler.Sharable
class OverrideStringsPacketHandler(private val strings: Map<String, String>) : ChannelDuplexHandler() {
    override fun write(ctx: ChannelHandlerContext, packet: Any, promise: ChannelPromise) {
        if (packet is ClientboundChatPacket) {
            val keyArgs = when {
                packet.message is TranslatableComponent -> {
                    val key = (packet.message as TranslatableComponent).key
                    val args = (packet.message as TranslatableComponent).args
                    key to args
                }
                packet.message is AdventureComponent -> {
                    val adventure = unsafeGetField(AdventureComponent::class.java, "adventure", packet.message) as net.kyori.adventure.text.Component
                    if (adventure is net.kyori.adventure.text.TranslatableComponent) {
                        val key = adventure.key()
                        val args = adventure.args()
                        key to args
                    } else null
                }
                packet.`adventure$message` is net.kyori.adventure.text.TranslatableComponent -> {
                    val key = (packet.`adventure$message` as net.kyori.adventure.text.TranslatableComponent).key()
                    val args = (packet.`adventure$message` as net.kyori.adventure.text.TranslatableComponent).args()
                    key to args
                }
                else -> null
            }
            if (keyArgs != null) {
                val (key, args) = keyArgs
                val replace = strings[key]
                if (replace != null) {
                    val text = replace.templateText("args" to args)
                    val field = ClientboundChatPacket::class.java.declaredFields.first { it.type == Component::class.java }
                    try {
                        unsafeSetField(field, packet, serverCommandSource.updateForEntity(text, null))
                        packet.components = null
                        packet.`adventure$message` = null
                    } catch (e: CommandSyntaxException) {
                        unsafeSetField(field, packet, text)
                        packet.components = null
                        packet.`adventure$message` = null
                    }
                }
            }
        }
        super.write(ctx, packet, promise)
    }
}
