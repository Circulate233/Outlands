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

package net.minecraftforge.client.settings;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.IntHashMap;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class KeyBindingMap
{
    private static final EnumMap<KeyModifier, IntHashMap<Collection<KeyBinding>>> map = new java.util.EnumMap<KeyModifier, IntHashMap<Collection<KeyBinding>>>(KeyModifier.class);
    static
    {
        for (KeyModifier modifier : KeyModifier.values())
        {
            map.put(modifier, new IntHashMap<Collection<KeyBinding>>());
        }
    }

    @Nullable
    @Deprecated // Cleanroom: Use lookupActives
    public KeyBinding lookupActive(int keyCode)
    {
        Set<KeyBinding> bindings = lookupActives(keyCode);
        return bindings.isEmpty() ? null : bindings.iterator().next();
    }

    /**
     * The bindings a key press should act on.
     * <p>
     * A binding fires when its main key is the one pressed, its conflict context is active, its modifier state
     * matches, and every key of its chord is held. When several of them pass, only the most specific ones do: a
     * binding on {@code Ctrl+Shift+G} beats one on {@code G}, which is the whole point of allowing a chord, and
     * bindings of equal specificity all fire together so that two mods using the same key both see it.
     * <p>
     * Specificity is the chord size, decided here for all candidates at once. Deciding it per candidate, in map
     * order, would let whichever binding the map happens to yield first win - the defect this deliberately avoids.
     */
    public Set<KeyBinding> lookupActives(int keyCode)
    {
        List<KeyBinding> candidates = lookupAll(keyCode);
        if (candidates.isEmpty())
        {
            return Collections.emptySet();
        }
        List<KeyBinding> active = new ArrayList<>(candidates.size());
        int best = -1;
        for (KeyBinding binding : candidates)
        {
            if (!binding.isActiveAndMatches(keyCode) || !binding.isChordHeld())
            {
                continue;
            }
            int specificity = binding.getChordKeys().length;
            if (specificity > best)
            {
                best = specificity;
                active.clear();
            }
            if (specificity == best)
            {
                active.add(binding);
            }
        }
        return active.isEmpty() ? Collections.emptySet() : new LinkedHashSet<>(active);
    }

    public List<KeyBinding> lookupAll(int keyCode)
    {
        List<KeyBinding> matchingBindings = new ArrayList<KeyBinding>();
        for (IntHashMap<Collection<KeyBinding>> bindingsMap : map.values())
        {
            Collection<KeyBinding> bindings = bindingsMap.lookup(keyCode);
            if (bindings != null)
            {
                matchingBindings.addAll(bindings);
            }
        }
        return matchingBindings;
    }

    public void addKey(int keyCode, KeyBinding keyBinding)
    {
        KeyModifier keyModifier = keyBinding.getKeyModifier();
        IntHashMap<Collection<KeyBinding>> bindingsMap = map.get(keyModifier);
        Collection<KeyBinding> bindingsForKey = bindingsMap.lookup(keyCode);
        if (bindingsForKey == null)
        {
            bindingsForKey = new ArrayList<KeyBinding>();
            bindingsMap.addKey(keyCode, bindingsForKey);
        }
        bindingsForKey.add(keyBinding);
    }

    public void removeKey(KeyBinding keyBinding)
    {
        KeyModifier keyModifier = keyBinding.getKeyModifier();
        int keyCode = keyBinding.getKeyCode();
        IntHashMap<Collection<KeyBinding>> bindingsMap = map.get(keyModifier);
        Collection<KeyBinding> bindingsForKey = bindingsMap.lookup(keyCode);
        if (bindingsForKey != null)
        {
            bindingsForKey.remove(keyBinding);
            if (bindingsForKey.isEmpty())
            {
                bindingsMap.removeObject(keyCode);
            }
        }
    }

    public void clearMap()
    {
        for (IntHashMap<Collection<KeyBinding>> bindings : map.values())
        {
            bindings.clearMap();
        }
    }
}
