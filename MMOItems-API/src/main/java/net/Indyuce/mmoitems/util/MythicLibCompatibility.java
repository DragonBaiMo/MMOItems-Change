package net.Indyuce.mmoitems.util;

import io.lumine.mythic.lib.gson.JsonObject;
import io.lumine.mythic.lib.data.SynchronizedDataManager;
import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.player.particle.ParticleInformation;
import io.lumine.mythic.lib.profile.DefaultProfileDataModule;
import org.bukkit.Color;
import org.bukkit.Particle;

import java.lang.reflect.Constructor;

public final class MythicLibCompatibility {
    private static final Constructor<ParticleInformation> PARTICLE_CTOR;
    private static final boolean PARTICLE_CTOR_HAS_SIZE;
    private static final Constructor<ParticleInformation> JSON_CTOR;
    private static final Constructor<?> PROFILE_MODULE_CTOR;
    private static final boolean PROFILE_MODULE_USES_MANAGER;

    static {
        Constructor<ParticleInformation> particleCtor = null;
        boolean hasSize = false;
        try {
            particleCtor = ParticleInformation.class.getConstructor(
                    Particle.class, int.class, float.class, double.class, Color.class, float.class);
            hasSize = true;
        } catch (NoSuchMethodException ignored) {
            try {
                particleCtor = ParticleInformation.class.getConstructor(
                        Particle.class, int.class, float.class, double.class, Color.class);
            } catch (NoSuchMethodException ignoredAgain) {
                try {
                    particleCtor = ParticleInformation.class.getConstructor(
                            Particle.class, int.class, float.class, double.class, Object.class);
                } catch (NoSuchMethodException ignoredFinal) {
                    particleCtor = null;
                }
            }
        }
        PARTICLE_CTOR = particleCtor;
        PARTICLE_CTOR_HAS_SIZE = hasSize;

        Constructor<ParticleInformation> jsonCtor = null;
        try {
            jsonCtor = ParticleInformation.class.getConstructor(JsonObject.class);
        } catch (NoSuchMethodException ignored) {
            jsonCtor = null;
        }
        JSON_CTOR = jsonCtor;

        Constructor<?> profileCtor = null;
        boolean usesManager = false;
        try {
            profileCtor = DefaultProfileDataModule.class.getConstructor(MMOPlugin.class);
        } catch (NoSuchMethodException ignored) {
            try {
                profileCtor = DefaultProfileDataModule.class.getConstructor(SynchronizedDataManager.class);
                usesManager = true;
            } catch (NoSuchMethodException ignoredAgain) {
                profileCtor = null;
            }
        }
        PROFILE_MODULE_CTOR = profileCtor;
        PROFILE_MODULE_USES_MANAGER = usesManager;
    }

    private MythicLibCompatibility() {
    }

    public static ParticleInformation createParticle(Particle particle) {
        return createParticle(particle, 1, 0f, 0d, null, 1f);
    }

    public static ParticleInformation createParticle(Particle particle, int amount, float speed, double rOffset, Color color, float size) {
        if (PARTICLE_CTOR == null)
            throw new IllegalStateException("Unsupported MythicLib ParticleInformation signature");
        try {
            if (PARTICLE_CTOR_HAS_SIZE)
                return PARTICLE_CTOR.newInstance(particle, amount, speed, rOffset, color, size);
            return PARTICLE_CTOR.newInstance(particle, amount, speed, rOffset, color);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to create ParticleInformation", ex);
        }
    }

    public static ParticleInformation createParticle(JsonObject object) {
        if (JSON_CTOR != null) {
            try {
                return JSON_CTOR.newInstance(object);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        try {
            final String particleName = object.get("Particle").getAsString();
            final int amount = object.get("Amount").getAsInt();
            final double offset = object.get("Offset").getAsDouble();
            final boolean colored = object.get("Colored").getAsBoolean();
            if (colored) {
                final int red = object.get("Red").getAsInt();
                final int green = object.get("Green").getAsInt();
                final int blue = object.get("Blue").getAsInt();
                return createParticle(Particle.valueOf(particleName), amount, 0f, offset, Color.fromRGB(red, green, blue), 1f);
            }

            final float speed = object.has("Speed") ? object.get("Speed").getAsFloat() : 0f;
            return createParticle(Particle.valueOf(particleName), amount, speed, offset, null, 1f);
        } catch (Exception ex) {
            throw new IllegalStateException("Unsupported particle json format", ex);
        }
    }

    public static Object createProfileDataModule(SynchronizedDataManager<?, ?> manager) {
        if (PROFILE_MODULE_CTOR == null)
            throw new IllegalStateException("Unsupported MythicLib DefaultProfileDataModule signature");
        try {
            if (PROFILE_MODULE_USES_MANAGER) {
                return PROFILE_MODULE_CTOR.newInstance(manager);
            }
            return PROFILE_MODULE_CTOR.newInstance(manager.getOwningPlugin());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to create DefaultProfileDataModule", ex);
        }
    }
}
