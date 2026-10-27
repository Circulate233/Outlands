package net.minecraftforge.client.modlist.screen;

import net.minecraftforge.client.modlist.screen.widget.DropdownMenu;

import javax.annotation.Nullable;

public interface DropdownMenuHandler {
    void setMenu(@Nullable DropdownMenu menu);
}
