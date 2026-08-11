package vito.cobblebrain.mixin;

import com.cobblemon.mod.common.api.pokemon.status.Statuses;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vito.cobblebrain.sensors.PokemonCommandsKt;

@SuppressWarnings({"ConstantConditions", "unused"})
@Mixin(value = PersistentStatusContainer.class, remap = false)
public class PersistentStatusContainerMixin {

    @Inject(method = "update", at = @At("RETURN"), require = 0)
    private void onUpdateReturn(Pokemon pokemon, CallbackInfo ci) {
        if (pokemon != null && PokemonCommandsKt.isCobblemonPokemonResting(pokemon)) {
            if (pokemon.getStatus() == null) {
                try {
                    pokemon.setStatus(new PersistentStatusContainer(Statuses.SLEEP, 999999));
                } catch (Throwable ignored) {}
            }
        }
    }
}
