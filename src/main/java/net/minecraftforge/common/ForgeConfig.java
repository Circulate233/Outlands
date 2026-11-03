package net.minecraftforge.common;

import java.io.File;
import java.io.IOException;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;

/**
 * The one {@link Configuration} behind {@code config/forge.cfg}.
 *
 * <p>There used to be two independent objects writing one file: the early settings and {@code ForgeModContainer} each
 * opened their own configuration. A {@link Configuration} writes the whole file on {@code save()}, so two objects for
 * one file overwrite each other. Early settings now live in {@code forge.cfg} under {@code [early]} and every caller
 * goes through this holder.
 *
 * <p>The instance is created before the loader has resolved its directories (the early settings are needed to create
 * the window), so the directory comes from the launch home rather than {@code Loader#getConfigDir()}. It is
 * canonicalised so the loader's later, canonical spelling finds the same object.
 */
public final class ForgeConfig
{
    private static Configuration config;

    private ForgeConfig() {}

    /** Returns the shared configuration, creating it in {@code configDir} the first time. */
    public static synchronized Configuration get(File configDir)
    {
        if (config == null)
        {
            File dir = canonical(configDir);
            config = new Configuration(new File(dir, "forge.cfg"));
        }
        return config;
    }

    /** Returns the shared configuration using the launch home as the instance directory. */
    public static synchronized Configuration get()
    {
        Object home = FMLInjectionData.data()[6];
        File instance = home instanceof File file ? file : Launch.minecraftHome;
        if (instance == null)
            instance = new File(".");
        return get(new File(instance, "config"));
    }

    /** Test seam: drops the cached instance so a test can point it at a temporary directory. */
    public static synchronized void reset()
    {
        config = null;
    }

    private static File canonical(File dir)
    {
        try
        {
            return dir.getCanonicalFile();
        }
        catch (IOException e)
        {
            return dir.getAbsoluteFile();
        }
    }
}
