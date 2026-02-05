package net.Indyuce.mmoitems.manager.data;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.data.DefaultOfflineDataHolder;
import io.lumine.mythic.lib.data.SynchronizedDataManager;
import net.Indyuce.mmoitems.util.MythicLibCompatibility;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.player.PlayerData;
import org.jetbrains.annotations.NotNull;

public class PlayerDataManager extends SynchronizedDataManager<PlayerData, DefaultOfflineDataHolder> {
    public PlayerDataManager(MMOItems plugin) {
        super((io.lumine.mythic.lib.module.MMOPlugin) plugin);
    }

    @Override
    public PlayerData newPlayerData(@NotNull MMOPlayerData mmoPlayerData) {
        return new PlayerData(mmoPlayerData);
    }

    @Override
    public Object newProfileDataModule() {
        return MythicLibCompatibility.createProfileDataModule(this);
    }

    @Override
    public void loadEmptyPlayerData(@NotNull PlayerData playerData) {
        // Nothing to do
    }
}
