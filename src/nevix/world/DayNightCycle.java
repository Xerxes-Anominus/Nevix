package nevix.world;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Call;
import mindustry.net.NetConnection;

import static mindustry.Vars.headless;
import static mindustry.Vars.net;
import static mindustry.Vars.netClient;
import static mindustry.Vars.state;

public final class DayNightCycle {
    static final String packet = "nevix-daynight";
    static final float maxDark = 1f;
    static final long syncInterval = 30000L;
    
    static long localOffset;
    static double localCycle;
    
    static boolean synced;
    static long syncOffset;
    static double syncCycle;

    static long lastSend;
    static boolean applied, origLighting;
    static Color origAmbient = new Color();
    
    static final float pushStep = 0.01f;
    static float lastPushedDark = -1f;

    private DayNightCycle() {}

    static double cycleMs() {
        return Core.settings.getInt("cycle-time", 10) * 60000.0;
    }

    public static void init() {
        localCycle = cycleMs();
        if (!Core.settings.has("daynight-offset")) {
            Core.settings.put("daynight-offset", (long) (Mathf.random() * localCycle));
        }
        localOffset = Core.settings.getLong("daynight-offset", 0L);
        
        if (!headless) {
            netClient.addPacketHandler(packet, DayNightCycle::receive);
        }
        
        Events.on(PlayerJoin.class, e -> {
            if (net.server() && e.player.con != null) {
                sendTo(e.player.con);
            }
        });
        
        Events.on(PlayerLeave.class, e -> synced = false);

        Events.on(WorldLoadEvent.class, e -> {
            origLighting = state.rules.lighting;
            origAmbient.set(state.rules.ambientLight);
            applied = false;
        });

        Events.run(Trigger.update, () -> {
            phase();
            
            if (net.server() && Time.timeSinceMillis(lastSend) > syncInterval) {
                Call.clientPacketReliable(packet, payload());
                lastSend = Time.millis();
            }
            
            if (net.client() || !state.isGame()) return;
            
            if (Core.settings.getBool("map-darkening", true)) {
                float dark = (1f - daylight()) * maxDark;
                state.rules.lighting = dark > 0.01f;
                state.rules.ambientLight.set(0.02f, 0.03f, 0.10f, dark);
                applied = true;
                
                if (net.server() && Math.abs(dark - lastPushedDark) > pushStep) {
                    Call.setRules(state.rules);
                    lastPushedDark = dark;
                }
            } else if (applied) {
                state.rules.lighting = origLighting;
                state.rules.ambientLight.set(origAmbient);
                applied = false;
                if (net.server()) Call.setRules(state.rules);
                lastPushedDark = -1f;
            }
        });
    }
    
    public static void registerCommands(CommandHandler handler) {
        handler.register("daynight", "<minutes>", "Set day/night cycle length (5-60).", args -> {
            if (!Strings.canParseInt(args[0])) {
                Log.err("Minutes must be a number.");
                return;
            }
            int m = Mathf.clamp(Strings.parseInt(args[0]), 5, 60);
            Core.settings.put("cycle-time", m);
            Log.info("Day/night cycle: @ min.", m);
        });
    }
    
    static String payload() {
        long now = System.currentTimeMillis();
        long cyc = (long) localCycle;
        return ((now + localOffset) % cyc) + "," + cyc;
    }

    static void sendTo(NetConnection con) {
        Call.clientPacketReliable(con, packet, payload());
    }

    static void receive(String content) {
        try {
            String[] p = content.split(",");
            long elapsed = Long.parseLong(p[0]);
            long cyc = Long.parseLong(p[1]);
            if (cyc <= 0) return;

            long now = System.currentTimeMillis();
            syncCycle = cyc;
            syncOffset = Math.floorMod(elapsed - (now % cyc), cyc);
            synced = true;
        } catch (Exception ignored) {
        }
    }
    
    public static float phase() {
        long now = System.currentTimeMillis();
        
        if (net.client() && synced) {
            return (float) (((now + syncOffset) % syncCycle) / syncCycle);
        }

        double newCycle = cycleMs();
        if (newCycle != localCycle) {
            double p = ((now + localOffset) % localCycle) / localCycle;
            localOffset = Math.floorMod((long) (p * newCycle) - (long) (now % newCycle), (long) newCycle);
            localCycle = newCycle;
            Core.settings.put("daynight-offset", localOffset);
            lastSend = 0L;
        }
        return (float) (((now + localOffset) % localCycle) / localCycle);
    }
    
    public static float daylight() {
        return 0.5f + 0.5f * Mathf.cos(phase() * Mathf.PI2);
    }
    
    public static boolean isDay() {
        return daylight() >= 0.5f;
    }
}