package com.tntbbp.myminecraft.web.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tntbbp.myminecraft.MyMinecraftPlugin;
import com.tntbbp.myminecraft.manager.raid.RaidBossManager;
import com.tntbbp.myminecraft.model.Stock;
import com.tntbbp.myminecraft.model.Team;
import com.tntbbp.myminecraft.util.SpecialItemCatalog;
import com.tntbbp.myminecraft.web.BridgeException;
import com.tntbbp.myminecraft.web.BridgeExchange;
import com.tntbbp.myminecraft.web.BridgeResponse;
import com.tntbbp.myminecraft.web.CommandRunner;
import com.tntbbp.myminecraft.web.PlayerNames;
import com.tntbbp.myminecraft.web.WebBridge;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 폼 선택 목록용 카탈로그 {@code GET /catalog/{kind}} (api-bridge.md §2.11).
 * kind ∈ materials | special-items | worlds | stocks | teams | raid-bosses.
 */
public class CatalogHandler {

    private final WebBridge bridge;
    /** 재질 목록은 서버가 켜져 있는 동안 바뀌지 않으므로 한 번만 만든다. */
    private volatile List<String> materials;

    public CatalogHandler(WebBridge bridge) {
        this.bridge = bridge;
    }

    public BridgeResponse catalog(BridgeExchange exchange) throws Exception {
        String kind = exchange.pathParam("kind");
        JsonObject body = switch (kind == null ? "" : kind) {
            case "materials" -> materials();
            case "special-items" -> bridge.callSync(this::specialItems);
            case "worlds" -> bridge.callSync(CatalogHandler::worlds);
            case "stocks" -> bridge.callSync(this::stocks);
            case "teams" -> bridge.callSync(this::teams);
            case "raid-bosses" -> bridge.callSync(this::raidBosses);
            default -> throw BridgeException.notFound("알 수 없는 카탈로그입니다: " + kind);
        };
        return BridgeResponse.ok(body);
    }

    private JsonObject materials() throws Exception {
        List<String> names = materials;
        if (names == null) {
            names = bridge.callSync(() -> {
                List<String> result = new ArrayList<>();
                for (Material material : Material.values()) {
                    if (material.isLegacy() || material.isAir() || !material.isItem()) {
                        continue;
                    }
                    result.add(material.name());
                }
                return List.copyOf(result);
            });
            materials = names;
        }
        JsonArray array = new JsonArray();
        names.forEach(array::add);
        JsonObject body = new JsonObject();
        body.add("materials", array);
        return body;
    }

    private JsonObject specialItems() {
        JsonArray items = new JsonArray();
        for (SpecialItemCatalog.CatalogEntry entry : SpecialItemCatalog.catalog(bridge.plugin())) {
            JsonObject item = new JsonObject();
            item.addProperty("name", entry.name());
            item.addProperty("display_name", entry.displayName());
            item.addProperty("material", entry.material());
            item.addProperty("model_data", entry.modelData());
            item.addProperty("category", entry.category());
            items.add(item);
        }
        JsonObject body = new JsonObject();
        body.add("items", items);
        return body;
    }

    private static JsonObject worlds() {
        JsonArray worlds = new JsonArray();
        for (World world : Bukkit.getWorlds()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", world.getName());
            entry.addProperty("environment", world.getEnvironment().name());
            worlds.add(entry);
        }
        JsonObject body = new JsonObject();
        body.add("worlds", worlds);
        return body;
    }

    private JsonObject stocks() {
        JsonArray stocks = new JsonArray();
        for (Stock stock : bridge.plugin().getStockManager().getStocks()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", stock.getId());
            entry.addProperty("name", stock.getName());
            stocks.add(entry);
        }
        JsonObject body = new JsonObject();
        body.add("stocks", stocks);
        return body;
    }

    private JsonObject teams() {
        MyMinecraftPlugin plugin = bridge.plugin();
        JsonArray teams = new JsonArray();
        for (String key : plugin.getTeamManager().teamNames()) {
            Team team = plugin.getTeamManager().getTeam(key);
            if (team == null) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("name", team.name());
            entry.addProperty("leader_name", PlayerNames.nameOrUuid(team.leader()));
            entry.addProperty("member_count", team.members().size());
            teams.add(entry);
        }
        JsonObject body = new JsonObject();
        body.add("teams", teams);
        return body;
    }

    private JsonObject raidBosses() {
        RaidBossManager manager = bridge.plugin().getRaidBossManager();
        JsonArray bosses = new JsonArray();
        for (String id : RaidBossManager.BOSS_IDS) {
            String name = manager.displayNameOf(id);
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("name", name == null ? id : CommandRunner.stripLegacyCodes(name));
            bosses.add(entry);
        }
        JsonObject body = new JsonObject();
        body.add("bosses", bosses);
        return body;
    }
}
