package de.kallifabio.cloud.pluginapi.request;

import java.util.Objects;

public final class PlayerDataSaveRequestBuilder {

    private String playerUuid;
    private int coins;
    private int kills;
    private int deaths;
    private int wins;
    private int losses;
    private String rank = "default";

    public static PlayerDataSaveRequestBuilder create() {
        return new PlayerDataSaveRequestBuilder();
    }

    public PlayerDataSaveRequestBuilder playerUuid(String playerUuid) {
        this.playerUuid = playerUuid;
        return this;
    }

    public PlayerDataSaveRequestBuilder coins(int coins) {
        this.coins = coins;
        return this;
    }

    public PlayerDataSaveRequestBuilder kills(int kills) {
        this.kills = kills;
        return this;
    }

    public PlayerDataSaveRequestBuilder deaths(int deaths) {
        this.deaths = deaths;
        return this;
    }

    public PlayerDataSaveRequestBuilder wins(int wins) {
        this.wins = wins;
        return this;
    }

    public PlayerDataSaveRequestBuilder losses(int losses) {
        this.losses = losses;
        return this;
    }

    public PlayerDataSaveRequestBuilder rank(String rank) {
        this.rank = rank == null ? "default" : rank;
        return this;
    }

    public PlayerDataSaveRequest build() {
        return new PlayerDataSaveRequest(
                Objects.requireNonNull(playerUuid, "playerUuid"),
                coins,
                kills,
                deaths,
                wins,
                losses,
                rank
        );
    }
}
