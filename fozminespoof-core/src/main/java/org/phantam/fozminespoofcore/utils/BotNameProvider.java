package org.phantam.fozminespoofcore.utils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class BotNameProvider {

    private static final List<String> MINECRAFT_NAMES = List.of(
            "Alex", "Steve", "Notch", "Jeb", "C418", "Herobrine", "Dream", "Technoblade", "GeorgeNotFound", "Sapnap",
            "BadBoyHalo", "Antfrost", "Skeppy", "CaptainSparklez", "DanTDM", "PopularMMOs", "GamingWithJen", "Stampylongnose", "iBallisticSquid", "LDShadowLady",
            "SmallishBeans", "Joel", "Lizzie", "Grian", "MumboJumbo", "Iskall85", "Stressmonster", "FalseSymmetry", "Rendog", "Docm77",
            "EthosLab", "VintageBeef", "BdoubleO100", "Keralis", "Welsknight", "xisumavoid", "ImpulseSV", "TangoTek", "ZombieCleo", "Cubfan135",
            "Hermitcraft", "Hypixel", "Simon", "Lewis", "Yogscast", "Sips", "Sjin", "Duncan", "Hannah",
            "Lachlan", "PrestonPlayz", "Vikkstar123", "JeromeASF", "BajanCanadian", "SkyDoesMinecraft", "Deadlox", "TrueMU", "SSundee",
            "MrCrayfish", "Direwolf20", "SethBling", "Etho", "Guude", "PauseUnpause", "Beef", "Coistar", "Avividgenius", "Zisteau",
            "Pakratt0013", "jsano19", "Nebris", "Arkas", "thejustinw", "Millbee", "AnderZEL", "BlameTheTC", "Generikb", "BdoubleO",
            "Shadow", "Hunter", "Ghost", "Alpha", "Omega", "Phoenix", "Dragon", "Viper", "Cobra", "Titan",
            "Apex", "Nova", "Cosmo", "Lunar", "Solar", "Eclipse", "Nebula", "Galaxy", "Astral", "Comet",
            "Meteor", "Blaze", "Frost", "Storm", "Thunder", "Lightning", "ShadowPvP", "PvPMaster", "ComboGod", "WTapKing",
            "StrafeGod", "ClickSpeed", "CPS_Monster", "PingLord", "LagSwitch", "Knockback", "ComboBreaker", "RodTricks", "BowSpam", "TNT_Drop",
            "Bridger", "GodBridge", "Breezily", "TellyBridge", "ClutchGod", "BlockPlacer", "SkyWarsPro", "BedWarsKing", "UHC_Legend", "SG_Champion",
            "Mineplexer", "HiveMind", "HypixelTryhard", "MVP_Plus", "VIP_Player", "DefaultSkin", "StevesBrother", "AlexsSister", "GreenSteve", "RedAlex",
            "BlueSteve", "YellowAlex", "PinkSteve", "PurpleAlex", "OrangeSteve", "GreySteve", "WhiteSteve", "BlackSteve", "GoldSteve", "DiamondAlex",
            "IronSteve", "CoalAlex", "RedstoneSteve", "LapisAlex", "EmeraldSteve", "QuartzAlex", "NetheriteSteve", "ObsidianAlex", "BedrockSteve", "BarrierSteve",
            "AirSteve", "WaterAlex", "LavaSteve", "FireAlex", "WindSteve", "EarthAlex", "NatureSteve", "SpaceAlex", "TimeSteve", "DimensionAlex",
            "VoidSteve", "ChaosAlex", "OrderSteve", "LightAlex", "DarkSteve", "HolyAlex", "UnholySteve", "AngelAlex", "DemonSteve", "GodAlex",
            "GamerTag", "PlayerOne", "PlayerTwo", "NoobMaster", "ProGamer", "Hacker", "Cheater", "Glitcher", "Modder", "Admin",
            "Owner", "CoOwner", "Developer", "Builder", "Scripter", "Coder", "Programmer", "Designer", "Artist", "Musician",
            "Writer", "Reader", "Thinker", "Dreamer", "Doer", "Maker", "Creator", "Destroyer", "Protector", "Guardian",
            "Sentinel", "Vanguard", "Champion", "Hero", "Villain", "AntiHero", "Rogue", "Thief", "Assassin", "Ninja",
            "Samurai", "Knight", "Paladin", "Warrior", "Soldier", "Commander", "General", "King", "Queen", "Prince",
            "Princess", "Emperor", "Empress", "Lord", "Lady", "Duke", "Duchess", "Count", "Countess", "Baron",
            "Baroness", "KnightTemplar", "Crusader", "Gladiator", "Fighter", "Brawler", "Wrestler", "Boxer", "MartialArtist", "Monk",
            "Priest", "Cleric", "PaladinKnight", "Bishop", "Pope", "Saint", "Angel", "Archangel", "Seraph", "Cherub",
            "Demon", "Devil", "Satan", "Lucifer", "Beelzebub", "Asmodeus", "Leviathan", "Belphegor", "Mammon", "Abaddon",
            "Apollyon", "Azazel", "Bael", "Balam", "Barbatos", "Belial", "Bune", "Foras", "Gusion", "Murmur",
            "Purson", "Raum", "Sallos", "Seere", "Shax", "Stolas", "Valefor", "Vapula", "Vassago", "Zepar",
            "Zeus", "Hera", "Poseidon", "Demeter", "Athena", "Apollo", "Artemis", "Ares", "Aphrodite", "Hephaestus",
            "Hermes", "Hestia", "Dionysus", "Hades", "Persephone", "Eros", "Iris", "Hebe", "Ganymede", "Themis",
            "Chronos", "Rhea", "Oceanus", "Tethys", "Hyperion", "Theia", "Coeus", "Phoebe", "Crius", "Mnemosyne",
            "Iapetus", "Atlas", "Prometheus", "Epimetheus", "Menoetius", "Helios", "Selene", "Eos", "Leto", "Asteria",
            "Odin", "Frigg", "Thor", "Loki", "Balder", "Hod", "Hermod", "Heimdall", "Tyr", "Bragi",
            "Idun", "Njord", "Freyr", "Freya", "Ullr", "Vidar", "Vali", "Sif", "Forseti", "Kvasir",
            "Mimir", "Aegir", "Ran", "Hel", "Fenrir", "Jormungand", "Sleipnir", "Geri", "Freki", "Huginn",
            "Muninn", "Valkyrie", "Einherjar", "Norn", "Urdr", "Verdandi", "Skuld", "Elf", "Dwarf", "Gnome",
            "Troll", "Orc", "Goblin", "Hobgoblin", "Ogre", "Giant", "TitanGiant", "Cyclops", "Centaur", "Minotaur",
            "Satyr", "Faun", "Nymph", "Dryad", "Oread", "Nereid", "Oceanid", "Siren", "Mermaid", "Merman",
            "Triton", "Kraken", "LeviathanSea", "Hydra", "Chimera", "Sphinx", "Griffin", "Hippogriff", "Pegasus", "Unicorn",
            "Alicorn", "PhoenixBird", "Thunderbird", "Roc", "Harpy", "Gorgon", "Medusa", "Stheno", "Euryale", "Graeae",
            "Vampire", "Werewolf", "ZombieMob", "SkeletonMob", "Ghoul", "Wight", "Wraith", "Specter", "PhantomMob", "GhostSpirit",
            "Banshee", "Poltergeist", "Revenant", "Lich", "Necromancer", "Warlock", "Witch", "Sorcerer", "Sorceress", "Wizard",
            "Mage", "Magician", "Enchanter", "Enchantress", "Alchemist", "Astrologer", "Diviner", "Necro", "Pyromancer", "Cryomancer",
            "Electromancer", "Aeromancer", "Geomancer", "Hydromancer", "Chronomancer", "Biomancer", "Shadowmancer", "Photomancer", "Cosmomancer", "Nethermancer",
            "EnderPro", "CreeperBoom", "EndermanEye", "ZombieBrain", "SkeletonBone", "SpiderWeb", "CaveSpiderSpit", "SilverfishBite", "EndermiteMite", "WitherSkull",
            "StrayArrow", "HuskDesert", "DrownedTrident", "GuardianLaser", "PhantomWing", "BlazeRod", "MagmaCubeJump", "GhastTear",
            "PiglinGold", "PiglinBruteAxe", "HoglinTusk", "ZoglinRot", "StriderLava", "RavagerRam", "VindicatorAxe", "EvokerFang", "VexSword",
            "IllusionerBow", "WitchPotion", "SlimeBounce", "IronGolemSmash", "SnowGolemBall", "VillagerTrade", "ChickenEgg", "CowMilk", "PigPork",
            "SheepWool", "WolfBone", "OcelotFish", "CatPurr", "ParrotSeed", "RabbitFoot", "LlamaSpit", "HorseSaddle", "DonkeyChest", "MulePack",
            "FoxBerry", "PandaBamboo", "TurtleEggShell", "DolphinSwim", "CodRaw", "SalmonRaw", "PufferfishPoison", "SquidInk", "GlowSquidInk",
            "BeeHoney", "BatWing", "AxolotlBucket", "GlowSquidGlow", "GoatHorn", "FrogLeg", "TadpoleWater", "AllayItem", "WardenSonic", "CamelSaddle",
            "SnifferEgg", "BreezeRod", "ArmadilloScute", "BoggedArrow", "RedstoneDust", "RepeaterTick", "ComparatorLogic", "PistonPush", "StickyPistonPull", "ObserverUpdate",
            "DispenserShoot", "DropperDrop", "HopperCollect", "LecternBook", "TargetBullseye", "TripwireHook", "TrappedChestTrap", "TNT_Explode",
            "MinecartRide", "CommandBlockOp", "StructureBlockLoad", "JigsawBlockPiece", "BarrierBlockVoid", "LightBlockBright",
            "OakLog", "SpruceLog", "BirchLog", "JungleLog", "AcaciaLog", "DarkOakLog", "MangroveLog", "CherryLog", "CrimsonStem", "WarpedStem",
            "StoneBrick", "CobblestoneWall", "MossyStone", "SmoothStone", "GranitePolish", "DioriteBurn", "AndesiteSharp", "DeepslateDark", "TuftGreen", "CalciteWhite",
            "DripstonePoint", "AmethystShard", "BuddingAmethyst", "SandDesert", "RedSandMesa", "GravelDrop", "ClayBall", "MudBrick", "TerracottaColor",
            "NetherrackFire", "SoulSandSlow", "SoulSoilBlue", "GlowstoneLight", "MagmaBlockHeat", "NetherBrickRed", "BlackstonePolished", "GlowLichenCave",
            "EndStoneYellow", "PurpurBlockEnd", "EndRodLight", "DragonEggRare", "BedRespawn", "CraftingTableMake", "FurnaceSmelt", "SmokerCook", "BlastFurnaceMelt",
            "BrewingStandPotion", "CauldronWater", "AnvilRepair", "EnchantingTableLapiz", "SmithingTableTrim", "Loombanner", "CartographyTableMap", "FletchingTableBow", "BarrelFish",
            "BellRing", "CampfireSmoke", "SoulCampfireBlue", "ComposterBone", "ConduitPower", "BeaconEffect", "LodestoneCompass", "ShulkerBoxShell", "EnderChestSafe",
            "SkyBlocker", "BedWarsGod", "PixelMon", "CraftKing", "MineGod", "BlockMaster", "RedstonePro", "BuilderBoy", "PvPGirl", "Survivalist",
            "HardcoreGuy", "SpeedRunner", "DreamChaser", "ManHunter", "HitwPro", "TntRunKing", "SpleefGod", "ParkourMaster", "DropperPro", "SkyGridder",
            "AxeCombat", "SwordFighter", "ShieldBlock", "CrossbowSniper", "EnderPearlClutch", "TotemPop", "GappleChomper", "PotSplasher", "LingeringCloud", "TippedArrow",
            "Kilo_", "Mega_", "Giga_", "Tera_", "Peta_", "Exa_", "Zetta_", "Yotta_", "Nano_", "Pico_",
            "Femto_", "Atto_", "Zepto_", "Yocto_", "Alpha_", "Beta_", "Gamma_", "Delta_", "Epsilon_", "Zeta_",
            "Eta_", "Theta_", "Iota_", "Kappa_", "Lambda_", "Mu_", "Nu_", "Xi_", "Omicron_", "Pi_",
            "Rho_", "Sigma_", "Tau_", "Upsilon_", "Phi_", "Chi_", "Psi_", "Omega_", "Ares_", "Hades_",
            "Loki_", "Odin_", "Thor_", "Zeus_", "Anubis_", "Osiris_", "Ra_", "Horus_", "Isis_", "Set_",
            "Thoth_", "Bastet_", "Sobek_", "Sekhmet_", "Ptah_", "Nut_", "Geb_", "Shu_", "Tefnut_", "Amun_",
            "Mut_", "Khonsu_", "Nephthys_", "Anput_", "Wepwawet_", "Taweret_", "Bes_", "Hapi_", "Khnum_", "Satet_",
            "Anuket_", "Sesh_", "Mafdet_", "Serket_", "Neith_", "Heket_", "Meretseger_", "Renpet_", "Shai_", "Hu_",
            "Sia_", "Heka_", "Khepri_", "Kherty_", "Aker_", "Babi_", "WadjWer_", "Satis_", "Sopdu_", "Sopdet_",
            "Seker_", "Shezmu_", "Unut_", "Hedetet_", "Kebechet_", "Menhit_", "Iusaas_", "Wosret_",
            "xX_Shadow_Xx", "xX_Hunter_Xx", "xX_Ghost_Xx", "xX_Alpha_Xx", "xX_Omega_Xx", "xX_Phoenix_Xx", "xX_Dragon_Xx", "xX_Viper_Xx", "xX_Cobra_Xx", "xX_Titan_Xx",
            "i_Shadow_i", "i_Hunter_i", "i_Ghost_i", "i_Alpha_i", "i_Omega_i", "i_Phoenix_i", "i_Dragon_i", "i_Viper_i", "i_Cobra_i", "i_Titan_i",
            "Im_Shadow", "Im_Hunter", "Im_Ghost", "Im_Alpha", "Im_Omega", "Im_Phoenix", "Im_Dragon", "Im_Viper", "Im_Cobra", "Im_Titan",
            "The_Shadow", "The_Hunter", "The_Ghost", "The_Alpha", "The_Omega", "The_Phoenix", "The_Dragon", "The_Viper", "The_Cobra", "The_Titan",
            "Shadow_PvP", "Hunter_PvP", "Ghost_PvP", "Alpha_PvP", "Omega_PvP", "Phoenix_PvP", "Dragon_PvP", "Viper_PvP", "Cobra_PvP", "Titan_PvP",
            "Shadow_MC", "Hunter_MC", "Ghost_MC", "Alpha_MC", "Omega_MC", "Phoenix_MC", "Dragon_MC", "Viper_MC", "Cobra_MC", "Titan_MC",
            "Shadow_Plays", "Hunter_Plays", "Ghost_Plays", "Alpha_Plays", "Omega_Plays", "Phoenix_Plays", "Dragon_Plays", "Viper_Plays", "Cobra_Plays", "Titan_Plays",
            "Shadow_YT", "Hunter_YT", "Ghost_YT", "Alpha_YT", "Omega_YT", "Phoenix_YT", "Dragon_YT", "Viper_YT", "Cobra_YT", "Titan_YT",
            "Shadow123", "Hunter123", "Ghost123", "Alpha123", "Omega123", "Phoenix123", "Dragon123", "Viper123", "Cobra123", "Titan123",
            "Shadow2026", "Hunter2026", "Ghost2026", "Alpha2026", "Omega2026", "Phoenix2026", "Dragon2026", "Viper2026", "Cobra2026", "Titan2026",
            "kirito", "asuna", "naruto", "sasuke", "luffy", "zoro", "sanji", "goku", "vegeta", "gohan",
            "tanjiro", "nezuko", "zenitsu", "inosuke", "deku", "bakugo", "todoroki", "saitama", "genos", "boruto",
            "light", "l_lawliet", "misa", "near", "mello", "ichigo", "rukia", "uryu", "chad", "renji",
            "edward", "alphonse", "mustang", "hawkeye", "scar", "lelouch", "suzaku", "c_c", "kallen", "nunnaly",
            "gon", "killua", "kurapika", "leorio", "hisoka", "illumi", "chrollo", "meruem", "netero", "biscuit",
            "kaneki", "touka", "rize", "hide", "tsukiyama", "amon", "juuzou", "arima", "shinichi", "migi",
            "rimuru", "benimaru", "shuna", "shion", "souei", "hakurou", "milim", "ramiris", "guy_crimson", "veldora",
            "subaru", "emilia", "rem", "ram", "beatrice", "puck", "roswaal", "reinhard", "felix", "julius",
            "kazuma", "aqua", "megumin", "darkness", "eris", "wiz", "yunyun", "vanir", "chris", "kyouya",
            "shinomiya", "shirogane", "fujiwara", "ishigami", "iino", "hayasaka", "kashiwagi", "tsubasa", "miko", "kei",
            "tatsuya", "miyuki", "erika", "leo", "mizuki", "mikihiko", "honoka", "shizuku", "mayumi", "jyuumonji",
            "frieren", "himmel", "eisen", "heiter", "fern", "stark", "sein", "flamme", "serie", "lugner"
    );

    private static final List<String> FILTERED_MINECRAFT_NAMES = MINECRAFT_NAMES.stream()
            .filter(name -> name.length() >= 3 && name.length() <= 16 && name.matches("^[a-zA-Z0-9_]+$"))
            .collect(Collectors.toList());

    private BotNameProvider() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static List<String> getMinecraftNames() {
        return FILTERED_MINECRAFT_NAMES;
    }

    public static String getNextAvailableName(Set<String> usedNamesLower) {
        for (String name : FILTERED_MINECRAFT_NAMES) {
            if (!usedNamesLower.contains(name.toLowerCase())) {
                return name;
            }
        }

        int number = 1;
        while (true) {
            String botName = "Bot" + number;
            if (!usedNamesLower.contains(botName.toLowerCase())) {
                return botName;
            }
            number++;
        }
    }
}