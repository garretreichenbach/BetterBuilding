package org.schema.schine.input;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import org.schema.common.ParseException;
import org.schema.common.util.security.OperatingSystem;
import org.schema.schine.common.language.Lng;
import org.schema.schine.common.language.Translatable;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.network.StateInterface;
import org.schema.schine.network.client.ClientStateInterface;
import org.schema.schine.network.client.KBMapInterface;
import org.schema.schine.network.objects.remote.RemoteByteBuffer;
import org.schema.schine.network.objects.remote.RemoteShort;
import org.schema.schine.resource.FileExt;

public enum KeyboardMappings implements KBMapInterface {
    RADIAL_MENU(15, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_7;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    STRAFE_LEFT(30, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_8;
        }
    }, KeyboardContext.PLAYER, (short)1),
    STRAFE_RIGHT(32, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_19;
        }
    }, KeyboardContext.PLAYER, (short)2),
    FORWARD(17, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_30;
        }
    }, KeyboardContext.PLAYER, (short)4),
    BACKWARDS(31, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_41;
        }
    }, KeyboardContext.PLAYER, (short)8),
    UP(18, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_52;
        }
    }, KeyboardContext.PLAYER, (short)16),
    DOWN(16, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_63;
        }
    }, KeyboardContext.PLAYER, (short)32),
    STRAFE_LEFT_SHIP(30, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_74;
        }
    }, KeyboardContext.SHIP, (short)1),
    STRAFE_RIGHT_SHIP(32, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_85;
        }
    }, KeyboardContext.SHIP, (short)2),
    FORWARD_SHIP(17, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_91;
        }
    }, KeyboardContext.SHIP, (short)4),
    BACKWARDS_SHIP(31, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_9;
        }
    }, KeyboardContext.SHIP, (short)8),
    UP_SHIP(57, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_10;
        }
    }, KeyboardContext.SHIP, (short)16),
    DOWN_SHIP(29, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_11;
        }
    }, KeyboardContext.SHIP, (short)32),
    ROTATE_LEFT_SHIP(16, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_12;
        }
    }, KeyboardContext.SHIP, (short)512),
    ROTATE_RIGHT_SHIP(18, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_13;
        }
    }, KeyboardContext.SHIP, (short)1024),
    SWITCH_FIRE_MODE(56, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_197;
        }
    }, KeyboardContext.SHIP, (short)1024),
    PLAYER_LIST(59, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_14;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    DROP_ITEM(14, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_15;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    RECORD_GIF(197, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_16;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    NETWORK_STATS_PANEL(88, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_18;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    LAG_STATS_PANEL(65, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_17;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    OBJECT_VIEW_CAM(43, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_20;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    SCROLL_MOUSE_ZOOM(42, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_21;
        }
    }, KeyboardContext.GENERAL, (short)-1, true),
    SCROLL_BOTTOM_BAR(56, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_22;
        }
    }, KeyboardContext.SHIP, (short)-1, true),
    BRAKE(42, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_23;
        }
    }, KeyboardContext.SHIP, (short)64),
    ROLL(56, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_24;
        }
    }, KeyboardContext.SHIP, (short)-1, true),
    CHANGE_SHIP_MODE(44, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_25;
        }
    }, KeyboardContext.SHIP, (short)128),
    JUMP(57, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_26;
        }
    }, KeyboardContext.PLAYER, (short)256),
    GRAPPLING_HOOK(57, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_27;
        }
    }, KeyboardContext.PLAYER, (short)-1),
    WALK(42, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_28;
        }
    }, KeyboardContext.PLAYER, (short)4096),
    JUMP_TO_MODULE(45, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_29;
        }
    }, KeyboardContext.BUILD, (short)-1),
    BUILD_MODE_FLASHLIGHT(25, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_211;
        }
    }, KeyboardContext.BUILD, (short)-1),
    REBOOT_SYSTEMS(21, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_31;
        }
    }, KeyboardContext.SHIP, (short)-1),
    FREE_CAM(54, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_32;
        }
    }, KeyboardContext.GENERAL, (short)2048),
    ADJUST_COCKPIT(25, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_209;
        }
    }, KeyboardContext.SHIP, (short)-1),
    ADJUST_COCKPIT_RESET(24, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_210;
        }
    }, KeyboardContext.SHIP, (short)-1),
    ENTER_SHIP(19, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_33;
        }
    }, KeyboardContext.SHIP, (short)-1),
    ACTIVATE(19, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_34;
        }
    }, KeyboardContext.PLAYER, (short)-1),
    TUTORIAL(66, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_35;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    CREW_CONTROL(OperatingSystem.getOS() == OperatingSystem.MAC ? 12 : 29, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_36;
        }
    }, KeyboardContext.PLAYER, (short)-1, true),
    STUCK_PROTECT(200, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_37;
        }
    }, KeyboardContext.PLAYER, (short)-1),
    SIT_ASTRONAUT(24, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_38;
        }
    }, KeyboardContext.PLAYER, (short)-1),
    SPAWN_SHIP(45, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_39;
        }
    }, KeyboardContext.PLAYER, (short)-1),
    SPAWN_SPACE_STATION(25, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_40;
        }
    }, KeyboardContext.PLAYER, (short)-1),
    SELECT_MODULE(46, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_42;
        }
    }, KeyboardContext.BUILD, (short)-1),
    CONNECT_MODULE(47, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_43;
        }
    }, KeyboardContext.BUILD, (short)-1),
    ASTRONAUT_ROTATE_BLOCK(29, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_44;
        }
    }, KeyboardContext.PLAYER, (short)-1, true),
    HELP_SCREEN(53, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_45;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    SHAPES_RADIAL_MENU(20, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_46;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    SWITCH_COCKPIT_SHIP_NEXT(200, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_47;
        }
    }, KeyboardContext.SHIP, (short)-1),
    SWITCH_COCKPIT_SHIP_PREVIOUS(208, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_48;
        }
    }, KeyboardContext.SHIP, (short)-1),
    SWITCH_COCKPIT_SHIP_HOLD_FOR_CHAIN(157, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_49;
        }
    }, KeyboardContext.SHIP, (short)-1, true),
    SWITCH_COCKPIT_NEXT(205, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_50;
        }
    }, KeyboardContext.SHIP, (short)-1),
    SWITCH_COCKPIT_PREVIOUS(203, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_93;
        }
    }, KeyboardContext.SHIP, (short)-1),
    REACTOR_KEY(210, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_51;
        }
    }, KeyboardContext.SHIP, (short)-1),
    CHAT(28, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_53;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    SHOP_PANEL(48, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_54;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    INVENTORY_SWITCH_ITEM(42, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_55;
        }
    }, KeyboardContext.GENERAL, (short)-1, true),
    INVENTORY_PANEL(23, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_56;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    WEAPON_PANEL(34, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_57;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    NAVIGATION_PANEL(49, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_58;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    AI_CONFIG_PANEL(39, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_59;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    CATALOG_PANEL(22, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_60;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    SELECT_ENTITY_NEXT(27, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_61;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    SELECT_ENTITY_PREV(26, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_62;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    SELECT_NEAREST_ENTITY(40, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_64;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    SELECT_LOOK_ENTITY(33, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_65;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    SELECT_OUTLINE(33, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_92;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    ZOOM_MINIMAP(13, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_66;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    RELEASE_MOUSE(60, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_67;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    NEXT_CONTROLLER(205, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_68;
        }
    }, KeyboardContext.BUILD, (short)-1),
    PREVIOUS_CONTROLLER(203, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_69;
        }
    }, KeyboardContext.BUILD, (short)-1),
    SELECT_CORE(200, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_70;
        }
    }, KeyboardContext.BUILD, (short)-1),
    BUILD_MODE_FIX_CAM(OperatingSystem.getOS() == OperatingSystem.MAC ? 12 : 29, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_71;
        }
    }, KeyboardContext.BUILD, (short)-1),
    //INSERTED CODE @682
    SWAP_CREATIVE_HOTBAR(56, new Translatable() {
        public final String getName(Enum var1) {
            return "Swap Creative Hotbar";
        }
    }, KeyboardContext.BUILD, (short)-1),
    //
    ALIGN_SHIP(46, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_72;
        }
    }, KeyboardContext.SHIP, (short)-1),
    CANCEL_SHIP(47, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_73;
        }
    }, KeyboardContext.SHIP, (short)-1),
    SCREENSHOT_WITH_GUI(63, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_75;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    SCREENSHOT_WITHOUT_GUI(64, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_76;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    FACTION_MENU(35, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_77;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    MAP_PANEL(50, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_78;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    STRUCTURE_PANEL(211, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_79;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    LEADERBOARD_PANEL(52, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_80;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    FLEET_PANEL(37, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_81;
        }
    }, KeyboardContext.GENERAL, (short)-1),
    BUILD_MODE_FAST_MOVEMENT(42, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_82;
        }
    }, KeyboardContext.BUILD, (short)-1, true),
    KEY_BULK_CONNECTION_MOD(42, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_83;
        }
    }, KeyboardContext.BUILD, (short)-1, true),
    COPY_AREA_NEXT(209, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_84;
        }
    }, KeyboardContext.BUILD, (short)-1, true),
    COPY_AREA_PRIOR(201, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_86;
        }
    }, KeyboardContext.BUILD, (short)-1, true),
    COPY_AREA_X_AXIS(42, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_87;
        }
    }, KeyboardContext.BUILD, (short)-1, true),
    COPY_AREA_Z_AXIS(29, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_88;
        }
    }, KeyboardContext.BUILD, (short)-1, true),
    PLAYER_TRADE_ACCEPT(36, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_89;
        }
    }, KeyboardContext.GENERAL, (short)-1, true),
    PLAYER_TRADE_CANCEL(38, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_90;
        }
    }, KeyboardContext.GENERAL, (short)-1, true),
    CREATIVE_MODE(61, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_94;
        }
    }, KeyboardContext.GENERAL, (short)-1, true),
    PIN_AI_TARGET(45, new Translatable() {
        public final String getName(Enum var1) {
            return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_208;
        }
    }, KeyboardContext.SHIP, (short)-1, true);

    public static final short t = 256;
    public static final KeyboardMappings[] remoteMappings;
    public static int version = 0;
    public static boolean dirty;
    private static KeyboardMappings[] byNameLength;
    public final boolean ignoreDuplicate;
    private final int value;
    private final Translatable description;
    private final KeyboardContext context;
    private final short ntKey;
    private int mapping;
    public static final Set<KeyboardMappings> duplicates;

    private KeyboardMappings(int var3, Translatable var4, KeyboardContext var5, short var6, boolean var7) {
        this.value = var3;
        this.description = var4;
        this.mapping = this.value;
        this.context = var5;
        this.ntKey = var6;
        this.ignoreDuplicate = var7;
    }

    private KeyboardMappings(int var3, Translatable var4, KeyboardContext var5, short var6) {
        this(var3, var4, var5, var6, false);
    }

    public final String getDescription() {
        return this.description.getName(this);
    }

    public static void read() {
        BufferedReader var0 = null;
        boolean var12 = false;

        label188: {
            label189: {
                try {
                    var12 = true;
                    ObjectArrayList var1 = new ObjectArrayList();
                    ObjectArrayList var2 = new ObjectArrayList();
                    FileExt var3 = new FileExt("./keyboard.cfg");
                    var0 = new BufferedReader(new FileReader(var3));
                    int var4 = 0;

                    String var20;
                    while((var20 = var0.readLine()) != null) {
                        if (!var20.trim().startsWith("//")) {
                            if (var20.contains("//")) {
                                var20 = var20.substring(0, var20.indexOf("//"));
                            }

                            String[] var21 = var20.split(" = ", 2);
                            var1.add(var21[0]);
                            var2.add(var21[1].trim());
                            if (var4 == 0 && !((String)var1.get(0)).equals("#version")) {
                                System.err.println("UNKNOWN VERSION!! RESETTING KEYS");
                                var12 = false;
                                break label189;
                            }

                            if (var4 == 0 && ((String)var1.get(0)).equals("#version") && Integer.parseInt((String)var2.get(var4)) != version) {
                                System.err.println("OLD VERSION!! RESETTING KEYS");
                            }

                            ++var4;
                        }
                    }

                    for(var4 = 1; var4 < var1.size(); ++var4) {
                        try {
                            int var22 = Keyboard.getKeyFromName((String)var2.get(var4));
                            valueOf((String)var1.get(var4)).setMapping(var22);
                        } catch (ParseException var17) {
                            var17.printStackTrace();
                        }
                    }

                    var12 = false;
                } catch (Exception var18) {
                    var18.printStackTrace();
                    System.err.println("Could not read settings file: using defaults (" + var18.getMessage() + ")");
                    var12 = false;
                    break label188;
                } finally {
                    if (var12) {
                        if (var0 != null) {
                            try {
                                var0.close();
                            } catch (IOException var13) {
                                var13.printStackTrace();
                            }
                        }

                        dirty = false;
                        checkForDuplicates();
                    }
                }

                try {
                    var0.close();
                } catch (IOException var16) {
                    var16.printStackTrace();
                }

                dirty = false;
                checkForDuplicates();
                return;
            }

            try {
                var0.close();
            } catch (IOException var15) {
                var15.printStackTrace();
            }

            dirty = false;
            checkForDuplicates();
            return;
        }

        if (var0 != null) {
            try {
                var0.close();
            } catch (IOException var14) {
                var14.printStackTrace();
            }
        }

        dirty = false;
        checkForDuplicates();
    }

    public static void writeDefault() {
        try {
            write("." + File.separator + "data" + File.separator + "config" + File.separator + "defaultSettings" + File.separator + "keyboard.cfg");
        } catch (IOException var0) {
            var0.printStackTrace();
        }
    }

    public static void write() throws IOException {
        write("./keyboard.cfg");
    }

    public static void write(String var0) throws IOException {
        FileExt var5;
        (var5 = new FileExt(var0)).delete();
        var5.createNewFile();
        BufferedWriter var6;
        (var6 = new BufferedWriter(new FileWriter(var5))).write("#version = " + version);
        var6.newLine();
        KeyboardMappings[] var1;
        int var2 = (var1 = values()).length;

        for(int var3 = 0; var3 < var2; ++var3) {
            KeyboardMappings var4 = var1[var3];
            var6.write(var4.name() + " = " + var4.getKeyCharAbsolute() + " //" + var4.getDescription());
            var6.newLine();
        }

        var6.flush();
        var6.close();
    }

    public static void main(String[] var0) {
        try {
            BufferedWriter var8 = new BufferedWriter(new FileWriter("./data/tutorial/KeyboardMappingVariables.txt"));
            KeyboardMappings[] var1;
            int var2 = (var1 = values()).length;

            for(int var3 = 0; var3 < var2; ++var3) {
                KeyboardMappings var4 = var1[var3];
                var8.append("$" + var4.name());
                int var5 = 50 - var4.name().length();
                var8.append(" ");

                for(int var6 = 0; var6 < var5; ++var6) {
                    var8.append(" ");
                }

                var8.append(" -> " + var4.getDescription() + "; Context: " + var4.context.name() + "\n");
            }

            var8.close();
        } catch (IOException var7) {
            var7.printStackTrace();
        }
    }

    public static boolean getEventKeyState(KeyEventInterface var0, InputState var1) {
        if (Controller.checkJoystick) {
            return var1 != null && var1.getController().isJoystickOk() && var0 instanceof JoystickEvent;
        } else {
            return var0.isPressed();
        }
    }

    public static int getEventKeySingle(KeyEventInterface var0) {
        if (isControlDown()) {
            return -2147483648;
        } else {
            return Controller.checkJoystick ? -2147483648 : var0.getKey();
        }
    }

    public static int getEventKeyRaw(KeyEventInterface var0) {
        return Controller.checkJoystick ? -2147483648 : var0.getKey();
    }

    public static String formatText(String var0) {
        KeyboardMappings[] var1;
        int var2 = (var1 = valuesByLength()).length;

        for(int var3 = 0; var3 < var2; ++var3) {
            KeyboardMappings var4 = var1[var3];
            if (var0.contains("$" + var4.name())) {
                try {
                    var0 = var0.replaceAll("\\$" + var4.name(), var4.getKeyChar());
                } catch (Exception var6) {
                    System.err.println("ERROR WHEN REPLACING TEXT CONTENT:\n" + var0 + "\nfor\n$" + var4.name() + " -> " + var4.getKeyChar());
                    var6.printStackTrace();
                }
            }
        }

        return var0;
    }

    public static boolean checkForDuplicates() {
        duplicates.clear();
        KeyboardMappings[] var0;
        int var1 = (var0 = values()).length;

        for(int var2 = 0; var2 < var1; ++var2) {
            KeyboardMappings var3 = var0[var2];
            KeyboardMappings[] var4;
            int var5 = (var4 = values()).length;

            for(int var6 = 0; var6 < var5; ++var6) {
                KeyboardMappings var7;
                if ((var7 = var4[var6]) != var3 && var7.getMapping() == var3.getMapping() && !var3.ignoreDuplicate && !var7.ignoreDuplicate && checkRelated(var3.getContext(), var7.getContext())) {
                    duplicates.add(var3);
                    duplicates.add(var7);
                }
            }
        }

        return true;
    }

    private static boolean checkRelated(KeyboardContext var0, KeyboardContext var1) {
        return isRelated(var0, var1) || isRelated(var1, var0);
    }

    private static boolean isRelated(KeyboardContext var0, KeyboardContext var1) {
        while(var0 != var1) {
            if (var0.isRoot()) {
                return false;
            }

            var0 = var0.getParent();
        }

        return true;
    }

    private static KeyboardMappings[] valuesByLength() {
        if (byNameLength == null) {
            Arrays.sort(byNameLength = (KeyboardMappings[])Arrays.copyOf(values(), values().length), new Comparator<KeyboardMappings>() {
                public final int compare(KeyboardMappings var1, KeyboardMappings var2) {
                    return var2.name().length() - var1.name().length();
                }
            });
        }

        return byNameLength;
    }

    private String getKeyCharAbsolute() {
        return Keyboard.getKeyName(this.getMapping());
    }

    public final boolean equalsNtKey(int var1) {
        return this.ntKey == var1;
    }

    public final int get() {
        return this.value;
    }

    public final KeyboardContext getContext() {
        return this.context;
    }

    public static String getKeyChar(int var0) {
        switch(var0) {
            case 14:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_0;
            case 29:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_4;
            case 39:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_202;
            case 42:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_2;
            case 43:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_1;
            case 51:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_203;
            case 54:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_3;
            case 56:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_204;
            case 57:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_6;
            case 157:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_5;
            case 184:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_205;
            case 199:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_198;
            case 201:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_199;
            case 207:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_201;
            case 209:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_200;
            case 210:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_207;
            case 211:
                return Lng.ORG_SCHEMA_SCHINE_INPUT_KEYBOARDMAPPINGS_206;
            default:
                return Keyboard.getKeyName(var0).toUpperCase(Locale.ENGLISH);
        }
    }

    public final String getKeyChar() {
        return getKeyChar(this.getMapping());
    }

    public final int getMapping() {
        return this.mapping;
    }

    public final void setMapping(int var1) {
        this.mapping = var1;
        dirty = true;
        checkForDuplicates();
    }

    public final boolean isDown(StateInterface var1) {
        return var1 instanceof InputState ? this.isDownSI((InputState)var1) : false;
    }

    public final boolean isDownSI(InputState var1) {
        return Keyboard.isKeyDown(this.getMapping()) || var1.getController().isJoystickKeyboardButtonDown(this);
    }

    public final boolean isNTKeyDown(Short var1) {
        assert this.ntKey > 0;

        return (var1 & this.ntKey) == this.ntKey;
    }

    public final void sendEvent(RemoteByteBuffer var1, boolean var2, boolean var3) {
        int var4 = this.ordinal() + 1;
        var1.add((byte)(var2 ? var4 : -var4));
    }

    public final void setNTKeyDown(RemoteShort var1, ClientStateInterface var2) {
        short var3;
        if (!Keyboard.isKeyDown(this.getMapping()) && !this.isSticky(var2) && !var2.getController().isJoystickKeyboardButtonDown(this)) {
            if ((var3 = (short)((Short)var1.get() & ~this.ntKey)) != (Short)var1.get()) {
                var1.set(var3, true);
            }

        } else {
            if ((var3 = (short)((Short)var1.get() | this.ntKey)) != (Short)var1.get()) {
                var1.set(var3, true);
            }

        }
    }

    public final boolean isSticky(StateInterface var1) {
        return this == FREE_CAM && Controller.FREE_CAM_STICKY;
    }

    public final boolean isEventKey(KeyEventInterface var1, ClientStateInterface var2) {
        if (isControlDown()) {
            return false;
        } else if (Controller.checkJoystick) {
            return var2.getController().isJoystickKeyboardButtonDown(this);
        } else {
            return getEventKeySingle(var1) == this.getMapping();
        }
    }

    public final boolean isDownOrSticky(StateInterface var1) {
        return this.isDown(var1) || this.isSticky(var1);
    }

    public static boolean isControlDown() {
        return Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157);
    }

    public static boolean isUndoButton(KeyEventInterface var0) {
        return isControlDown() && getEventKeyRaw(var0) == 44;
    }

    public static boolean isRedoButton(KeyEventInterface var0) {
        return isControlDown() && getEventKeyRaw(var0) == 21;
    }

    static {
        ObjectArrayList var0 = new ObjectArrayList();
        KeyboardMappings[] var1;
        int var2 = (var1 = values()).length;

        for(int var3 = 0; var3 < var2; ++var3) {
            KeyboardMappings var4;
            if ((var4 = var1[var3]).ntKey > 0) {
                var0.add(var4);
            }
        }

        remoteMappings = new KeyboardMappings[var0.size()];

        for(int var5 = 0; var5 < remoteMappings.length; ++var5) {
            remoteMappings[var5] = (KeyboardMappings)var0.get(var5);
        }

        duplicates = new ObjectOpenHashSet();
    }
}