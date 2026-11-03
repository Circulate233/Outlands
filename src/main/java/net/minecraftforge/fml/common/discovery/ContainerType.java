/*
 * Minecraft Forge
 * Copyright (c) 2016-2020.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.common.discovery;

import java.util.List;

import net.minecraftforge.fml.common.ModContainer;

/**
 * How a candidate's contents are found: a jar, a directory, or a type a mod added.
 *
 * <p>An enum on purpose, and it has to stay one. Mods extend it at runtime - {@code EnumHelper.addEnum(ContainerType.class,
 * name, new Class[]{Class.class}, new Object[]{myDiscovererClass})}, which lands in {@code $VALUES} through
 * {@code EnumHackery} - and then hand the constant to {@code ModCandidate}'s public constructors, whose {@code findMods}
 * dispatches to that mod's own {@link ITypeDiscoverer}. That contract is tied to the enum: {@code EnumHelper.addEnum}
 * takes a {@code Class<T extends Enum<?>>} at compile time, and mod bytecode reaches the constants through
 * {@code values()}, {@code valueOf()}, {@code ordinal()} and {@code switch}. Replacing this with a registry would delete
 * the extension point instead of modernising it, so it stays - see {@code SlotTable} for the sets that can move.
 */
public enum ContainerType
{
    JAR(JarDiscoverer.class),
    DIR(DirectoryDiscoverer.class);

    private final ITypeDiscoverer discoverer;

    ContainerType(Class<? extends ITypeDiscoverer> discovererClass)
    {
        try
        {
            this.discoverer = discovererClass.getConstructor().newInstance();
        }
        catch (ReflectiveOperationException e)
        {
            throw new RuntimeException(e);
        }
    }

    public List<ModContainer> findMods(ModCandidate candidate, ASMDataTable table)
    {
        return discoverer.discover(candidate, table);
    }
}
