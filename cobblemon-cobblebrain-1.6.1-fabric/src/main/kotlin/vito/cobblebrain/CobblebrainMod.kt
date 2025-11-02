package vito.cobblebrain

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import vito.cobblebrain.config.ConfigBuilder
import vito.cobblebrain.config.CobblebrainConfig
import vito.cobblebrain.social.DebugPartyCommand
import vito.cobblebrain.social.DialogueSystem.register
import java.io.File
import net.minecraft.server.MinecraftServer
import vito.cobblebrain.sensors.registerTickHandler
import vito.cobblebrain.social.PokemonTalkCommand


object CobblebrainMod : ModInitializer {
    @Suppress("MemberVisibilityCanBePrivate")
    const val MOD_ID = "cobblebrain"

    @Suppress("MemberVisibilityCanBePrivate")
    lateinit var config: CobblebrainConfig

    // Quando o jogo inicializa
    override fun onInitialize() {
        val pasta = File("cobblebrain-ai")

        // cria a pasta se não existir
        if (!pasta.exists()) {
            pasta.mkdirs()
        }

        // cria os arquivos dentro da pasta
        val file = File(pasta, "resposta_ia.txt")
        val file2 = File(pasta, "comando_ia.txt")

        // writeText já cria o arquivo se não existir
        file.writeText("")
        file2.writeText("")

        println("Arquivos prontos em: ${pasta.absolutePath}")
        config = ConfigBuilder.load(CobblebrainConfig::class.java, MOD_ID)
        println("o mod cobblebrain carregou")
        register()
        registerTickHandler()

        // Aqui registramos o comando
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            DebugPartyCommand.register(dispatcher)
            PokemonTalkCommand.register(dispatcher)
        }

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { _, _, _ ->
            config = ConfigBuilder.load(CobblebrainConfig::class.java, MOD_ID)

        }

        ServerLifecycleEvents.SERVER_STARTED.register { server: MinecraftServer ->
            currentServer = server
        }

        // limpa quando o servidor para
        ServerLifecycleEvents.SERVER_STOPPED.register {
            currentServer = null
        }
    }
}