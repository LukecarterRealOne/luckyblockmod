package com.lucky.mod;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Right-click: rolls a random ability.
 * Sneak + right-click: guaranteed YELLOW OMEGA BEAM.
 */
public class LuckyBlockItem extends Item {
    private static final DustParticleEffect YELLOW = new DustParticleEffect(new Vector3f(1.0f, 0.9f, 0.0f), 2.0f);
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0f, 0.65f, 0.0f), 1.4f);

    public LuckyBlockItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld sw)) {
            return TypedActionResult.success(stack);
        }

        int roll = user.isSneaking() ? 0 : world.random.nextInt(6);
        switch (roll) {
            case 0 -> omegaBeam(sw, user);
            case 1 -> lightningStorm(sw, user);
            case 2 -> meteorShower(sw, user);
            case 3 -> healingNova(sw, user);
            case 4 -> skyLaunch(sw, user);
            default -> goldRain(sw, user);
        }

        user.getItemCooldownManager().set(this, roll == 0 ? 80 : 40);
        return TypedActionResult.success(stack);
    }

    private void msg(PlayerEntity p, String text) {
        p.sendMessage(Text.literal(text), true);
    }

    // ---------- 0: YELLOW OMEGA BEAM ----------
    private void omegaBeam(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a7e\u00a7l\u26a1 YELLOW OMEGA BEAM! \u26a1");
        Vec3d eye = user.getEyePos();
        Vec3d dir = user.getRotationVec(1.0f);
        Vec3d end = eye.add(dir.multiply(64));
        var hit = sw.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, user));
        double len = hit.getType() == HitResult.Type.MISS ? 64 : eye.distanceTo(hit.getPos());

        sw.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3f, 0.8f);
        sw.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 2f, 1.5f);

        // Beam body: thick yellow core + white-hot end rod sparks + electric sparks
        Vec3d up = Math.abs(dir.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d right = dir.crossProduct(up).normalize();
        Vec3d realUp = right.crossProduct(dir).normalize();
        for (double d = 1.5; d <= len; d += 0.35) {
            Vec3d p = eye.add(dir.multiply(d)).add(0, -0.25, 0);
            sw.spawnParticles(YELLOW, p.x, p.y, p.z, 8, 0.3, 0.3, 0.3, 0);
            sw.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0.02);
            if (((int) (d / 0.35)) % 3 == 0) {
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.5, 0.5, 0.5, 0.3);
            }
            // spiral rings around the beam
            double ang = d * 2.2;
            Vec3d off = right.multiply(Math.cos(ang) * 0.9).add(realUp.multiply(Math.sin(ang) * 0.9));
            sw.spawnParticles(GOLD, p.x + off.x, p.y + off.y, p.z + off.z, 1, 0, 0, 0, 0);
            Vec3d off2 = off.multiply(-1);
            sw.spawnParticles(GOLD, p.x + off2.x, p.y + off2.y, p.z + off2.z, 1, 0, 0, 0, 0);
        }

        // Muzzle flash
        Vec3d muzzle = eye.add(dir.multiply(1.5));
        sw.spawnParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);

        // Damage everything along the beam
        Box box = new Box(eye, end);
        List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, box.expand(3.5), e -> e != user);
        for (LivingEntity e : targets) {
            Vec3d toE = e.getPos().add(0, e.getHeight() / 2, 0).subtract(eye);
            double t = MathHelper.clamp(toE.dotProduct(dir), 0, len);
            Vec3d closest = eye.add(dir.multiply(t));
            if (closest.distanceTo(e.getPos().add(0, e.getHeight() / 2, 0)) <= 3.0) {
                e.damage(sw.getDamageSources().indirectMagic(user, user), 40f);
                e.setOnFireFor(8);
                e.addVelocity(dir.x * 1.5, 0.5, dir.z * 1.5);
                e.velocityModified = true;
                sw.spawnParticles(ParticleTypes.EXPLOSION, e.getX(), e.getBodyY(0.5), e.getZ(), 1, 0, 0, 0, 0);
            }
        }

        // Impact explosion (no block damage) + shockwave ring
        Vec3d impact = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
        sw.createExplosion(user, impact.x, impact.y, impact.z, 4f, World.ExplosionSourceType.NONE);
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
        for (int i = 0; i < 72; i++) {
            double a = Math.toRadians(i * 5);
            sw.spawnParticles(YELLOW, impact.x + Math.cos(a) * 3, impact.y + 0.2, impact.z + Math.sin(a) * 3,
                    1, 0.05, 0.05, 0.05, 0);
        }
    }

    // ---------- 1: LIGHTNING STORM ----------
    private void lightningStorm(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a7b\u00a7lLightning Storm!");
        for (int i = 0; i < 8; i++) {
            double a = sw.random.nextDouble() * Math.PI * 2;
            double r = 4 + sw.random.nextDouble() * 6;
            Vec3d pos = user.getPos().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(pos);
                sw.spawnEntity(bolt);
            }
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1, pos.z, 30, 0.5, 1, 0.5, 0.3);
        }
    }

    // ---------- 2: METEOR SHOWER ----------
    private void meteorShower(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a7c\u00a7lMeteor Shower!");
        Vec3d eye = user.getEyePos();
        Vec3d target = eye.add(user.getRotationVec(1f).multiply(18));
        for (int i = 0; i < 6; i++) {
            Vec3d start = target.add((sw.random.nextDouble() - 0.5) * 12, 25 + sw.random.nextDouble() * 8,
                    (sw.random.nextDouble() - 0.5) * 12);
            FireballEntity fb = new FireballEntity(sw, user, new Vec3d(0, -1.2, 0), 2);
            fb.setPosition(start);
            sw.spawnEntity(fb);
            sw.spawnParticles(ParticleTypes.FLAME, start.x, start.y, start.z, 20, 0.5, 0.5, 0.5, 0.1);
        }
        sw.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 2f, 0.6f);
    }

    // ---------- 3: HEALING NOVA ----------
    private void healingNova(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a7a\u00a7lHealing Nova!");
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 2));
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, 3));
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 1));
        user.setHealth(user.getMaxHealth());
        for (int ring = 1; ring <= 4; ring++) {
            for (int i = 0; i < 40; i++) {
                double a = Math.toRadians(i * 9);
                sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, user.getX() + Math.cos(a) * ring,
                        user.getY() + 0.3 * ring, user.getZ() + Math.sin(a) * ring, 1, 0, 0, 0, 0);
            }
        }
        sw.spawnParticles(ParticleTypes.HEART, user.getX(), user.getY() + 1.5, user.getZ(), 20, 0.6, 0.6, 0.6, 0.1);
        sw.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 2f, 1.2f);
    }

    // ---------- 4: SKY LAUNCH ----------
    private void skyLaunch(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a7d\u00a7lSky Launch!");
        user.addVelocity(0, 2.2, 0);
        user.velocityModified = true;
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 300, 0));
        sw.spawnParticles(ParticleTypes.FIREWORK, user.getX(), user.getY(), user.getZ(), 80, 0.4, 0.2, 0.4, 0.3);
        sw.spawnParticles(ParticleTypes.CLOUD, user.getX(), user.getY(), user.getZ(), 40, 0.5, 0.1, 0.5, 0.2);
        sw.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 2f, 1f);
    }

    // ---------- 5: GOLD RAIN ----------
    private void goldRain(ServerWorld sw, PlayerEntity user) {
        msg(user, "\u00a76\u00a7lGold Rain!");
        for (int i = 0; i < 12; i++) {
            ItemStack loot = switch (sw.random.nextInt(5)) {
                case 0 -> new ItemStack(Items.DIAMOND, 1 + sw.random.nextInt(2));
                case 1 -> new ItemStack(Items.EMERALD, 1 + sw.random.nextInt(4));
                case 2 -> new ItemStack(Items.GOLDEN_APPLE, 1);
                default -> new ItemStack(Items.GOLD_INGOT, 2 + sw.random.nextInt(5));
            };
            double x = user.getX() + (sw.random.nextDouble() - 0.5) * 4;
            double z = user.getZ() + (sw.random.nextDouble() - 0.5) * 4;
            ItemEntity ie = new ItemEntity(sw, x, user.getY() + 4 + sw.random.nextDouble() * 2, z, loot);
            sw.spawnEntity(ie);
            sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, x, ie.getY(), z, 8, 0.2, 0.2, 0.2, 0.3);
        }
        sw.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 2f, 1f);
    }
}
