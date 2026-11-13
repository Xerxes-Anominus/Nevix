package nevix.game;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ai.WaveSpawner;
import mindustry.game.Difficulty;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Rules.TeamRule;
import mindustry.game.SpawnGroup;

import static mindustry.Vars.logic;
import static mindustry.Vars.net;
import static mindustry.Vars.state;

public enum NevixDifficulty {
    baby(2f, 0.5f, 0.5f, 0.5f, 2f, 2f),
    easy(1.5f, 0.75f, 0.75f, 0.75f, 1.5f, 1.5f),
    normal(1f, 1f, 1f, 1f, 1f, 1f),
    hard(0.85f, 1.2f, 1.25f, 1.25f, 1f, 1f),
    extreme(0.7f, 1.5f, 1.5f, 1.5f, 1f, 1f),
    eradication(0.6f, 2f, 1.75f, 1.75f, 0.9f, 0.9f),
    extinction(0.5f, 3f, 2.25f, 2.25f, 0.75f, 0.75f);

    public static final NevixDifficulty[] all = values();
    public static final String settingKey = "nevix-difficulty";

    public float
    waveTimeMultiplier,
    enemySpawnMultiplier,
    enemyDamageMultiplier,
    enemyHealthMultiplier,
    playerDamageMultiplier,
    playerHealthMultiplier;

    NevixDifficulty(float waveTimeMulti, float enemySpawnMulti, float enemyDamageMulti, float enemyHealthMulti, float playerDamageMulti, float playerHealthMulti) {
        this.waveTimeMultiplier = waveTimeMulti;
        this.enemySpawnMultiplier = enemySpawnMulti;
        this.enemyDamageMultiplier = enemyDamageMulti;
        this.enemyHealthMultiplier = enemyHealthMulti;
        this.playerDamageMultiplier = playerDamageMulti;
        this.playerHealthMultiplier = playerHealthMulti;
    }

    public String info() {
        String res =
        (waveTimeMultiplier == 1f ? "" :
            Core.bundle.format("difficulty.wave-time-multiplier", percentStatNeg(waveTimeMultiplier)) + "\n") +
        (enemySpawnMultiplier == 1f ? "" :
            Core.bundle.format("difficulty.enemy-spawn-multiplier", percentStat(enemySpawnMultiplier)) + "\n") +
        (enemyDamageMultiplier == 1f ? "" :
            Core.bundle.format("difficulty.enemy-damage-multiplier", percentStat(enemyDamageMultiplier)) + "\n") +
        (enemyHealthMultiplier == 1f ? "" :
            Core.bundle.format("difficulty.enemy-health-multiplier", percentStat(enemyHealthMultiplier)) + "\n") +
        (playerDamageMultiplier == 1f ? "" :
            Core.bundle.format("difficulty.player-damage-multiplier", percentStatNeg(playerDamageMultiplier)) + "\n") +
        (playerHealthMultiplier == 1f ? "" :
            Core.bundle.format("difficulty.player-health-multiplier", percentStatNeg(playerHealthMultiplier)) + "\n");

        return res.isEmpty() ? Core.bundle.get("difficulty.nomodifiers") : res;
    }

    public String localized() {
        return Core.bundle.get("difficulty." + name());
    }

    public static String percentStat(float value) {
        return ((int)(value * 100 - 100) > 0 ? "[negstat]+" : "[stat]") + (int)(value * 100 - 100) + "%[]";
    }

    public static String percentStatNeg(float value) {
        return ((int)(value * 100 - 100) > 0 ? "[stat]+" : "[negstat]") + (int)(value * 100 - 100) + "%[]";
    }

    public static NevixDifficulty current() {
        String n = Core.settings.getString(settingKey, normal.name());
        for (NevixDifficulty d : all) {
            if (d.name().equals(n)) return d;
        }
        return normal;
    }

    public static void set(NevixDifficulty d) {
        Core.settings.put(settingKey, d.name());
        applyRules();
    }
        
    public static void neutralizeVanilla() {
        if (state.getPlanet() != null) {
            state.getPlanet().campaignRules.difficulty = Difficulty.normal;
        }
    }
        
    public static void applyRules() {
        if (!state.isCampaign()) return;
        NevixDifficulty d = current();

        TeamRule enemy = state.rules.teams.get(state.rules.waveTeam);
        enemy.unitHealthMultiplier = d.enemyHealthMultiplier;
        enemy.unitDamageMultiplier = d.enemyDamageMultiplier;
        enemy.blockHealthMultiplier = d.enemyHealthMultiplier;
        enemy.blockDamageMultiplier = d.enemyDamageMultiplier;
        enemy.unitBuildSpeedMultiplier = d.enemySpawnMultiplier;
        enemy.unitCostMultiplier = 1f / d.enemySpawnMultiplier;

        TeamRule player = state.rules.teams.get(state.rules.defaultTeam);
        player.unitHealthMultiplier = d.playerHealthMultiplier;
        player.unitDamageMultiplier = d.playerDamageMultiplier;
        player.blockHealthMultiplier = d.playerHealthMultiplier;
        player.blockDamageMultiplier = d.playerDamageMultiplier;
    }

    public static void init() {
        Events.on(WorldLoadEvent.class, e -> {
            neutralizeVanilla();
            applyRules();
            Core.app.post(NevixDifficulty::applyRules);
        });

        Events.run(Trigger.update, NevixDifficulty::tickWaveTimer);
            
        Vars.spawner = new WaveSpawner() {
            @Override
            public void spawnEnemies() {
                float m = state.isCampaign() ? current().enemySpawnMultiplier : 1f;
                if (Mathf.equal(m, 1f)) {
                    super.spawnEnemies();
                    return;
                }

                Seq<SpawnGroup> groups = state.rules.spawns;
                int n = groups.size;
                int[] amount = new int[n], max = new int[n];
                float[] scaling = new float[n];

                for (int i = 0; i < n; i++) {
                    SpawnGroup g = groups.get(i);
                    amount[i] = g.unitAmount;
                    max[i] = g.max;
                    scaling[i] = g.unitScaling;

                    if (g.unitAmount > 0) g.unitAmount = Math.max(1, Math.round(g.unitAmount * m));
                    if (g.max != Integer.MAX_VALUE) g.max = Math.max(1, Math.round(g.max * m));
                    g.unitScaling = g.unitScaling / m;
                }

                try {
                    super.spawnEnemies();
                } finally {
                    for (int i = 0; i < n; i++) {
                        SpawnGroup g = groups.get(i);
                        g.unitAmount = amount[i];
                        g.max = max[i];
                        g.unitScaling = scaling[i];
                    }
                }
            }
        };
    }
        
    static void tickWaveTimer() {
        if (net.client() || !state.isCampaign() || !state.rules.waves || !state.rules.waveTimer || state.gameOver) return;
        if (logic.isWaitingWave()) return;

        float m = current().waveTimeMultiplier;
        if (Mathf.equal(m, 1f)) return;

        state.wavetime = Math.max(state.wavetime - Time.delta * (1f / m - 1f), 0f);
    }
}