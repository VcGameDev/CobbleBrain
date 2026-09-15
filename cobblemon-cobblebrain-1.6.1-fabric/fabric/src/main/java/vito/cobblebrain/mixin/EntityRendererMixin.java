package vito.cobblebrain.mixin;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vito.cobblebrain.social.StoryAssetManager;

@Mixin(LivingEntityRenderer.class)
public abstract class EntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {

    @Shadow
    protected M model;

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void cobblebrain$overrideRenderType(T entity, boolean bodyVisible, boolean translucent, boolean appearsGlowing, CallbackInfoReturnable<RenderType> cir) {
        ResourceLocation customTexture = StoryAssetManager.getEntityTextureOverride(entity.getId());
        if (customTexture != null) {
            if (translucent) {
                cir.setReturnValue(RenderType.itemEntityTranslucentCull(customTexture));
            } else if (bodyVisible) {
                cir.setReturnValue(this.model.renderType(customTexture));
            } else if (appearsGlowing) {
                cir.setReturnValue(RenderType.outline(customTexture));
            } else {
                cir.setReturnValue(null);
            }
        }
    }
}
