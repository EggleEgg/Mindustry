package mindustry.editor;

import arc.*;
import arc.func.*;
import arc.input.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

public class MapPatchesDialog extends BaseDialog{
    private float rowHeight = 50f;
    private float dragY;
    private int dragPointer = -1;
    private Table list;
    private Seq<Table> patchRows = new Seq<>();
    //making PatchSet a separate class would be cleaner
    private Seq<DataPatcher.PatchSet> visiblePatches = new Seq<>();
    private DataPatcher.PatchSet dragPatch;
    private boolean dragEnabled, dragMoved;

    public MapPatchesDialog(){
        super("@editor.patches");

        shown(this::setup);

        addCloseButton();
        buttons.button("@editor.patches.guide", Icon.link, () -> Core.app.openURI(patchesGuideURL)).size(200, 64f);

        buttons.button("@add", Icon.add, () -> showImport(this::addPatch)).size(200f, 64f);

        cont.top();
        getCell(cont).grow();

        cont.pane(t -> list = t);
    }

    private void setup(){
        list.clearChildren();
        patchRows.clear();
        var patches = state.patcher.patches;
        visiblePatches = patches.copy();

        //In editor dialogs, rules.mode() may not reflect map intent. Show all mode files for visibility.
        for(var mode : Gamemode.all){
            addRuntimePatch(visiblePatches, "datapatches/" + mode.name().toLowerCase() + ".json");
        }

        if(state.isCampaign() && state.getPlanet() != null && state.getPlanet().campaignRules.experimentalPatches){
            addRuntimePatch(visiblePatches, "datapatches/campaign.json");
        }

        if(visiblePatches.isEmpty()){
            list.add("@editor.patches.none");
        }else{
            Table t = list;
            for(var patch : visiblePatches){
                int fields = countFields(patch.json);

                Table row = new Table();
                row.defaults().pad(4f);

                if(patch.warnings.size > 0){
                    row.button(Icon.warning, Styles.graySquarei, iconMed, () -> {
                        BaseDialog dialog = new BaseDialog("@editor.patches.errors");
                        dialog.cont.top().pane(p -> {
                            p.top();

                            for(var warning : patch.warnings){
                                p.table(Styles.grayPanel, in -> {
                                    in.add(warning.replaceAll("\t", "  "), Styles.monoLabel).grow().wrap();
                                }).margin(6f).growX().pad(3f).row();
                            }
                        }).grow();
                        dialog.addCloseButton();
                        dialog.show();
                    }).size(rowHeight);
                }else{
                    row.add().size(rowHeight);
                }

                Button moveButton = row.button(Icon.move, Styles.graySquarei, iconMed, () -> {}).size(rowHeight).get();
                dragReorder(moveButton, patch);

                TextButton patchButton = new TextButton((patch.name.isEmpty() ? "<unnamed>\n" : "[accent]" + patch.name + "\n") + "[lightgray][[" + Core.bundle.format("editor.patch.fields", fields) + "]", Styles.grayt);
                patchButton.clicked(() -> {
                    BaseDialog dialog = new BaseDialog(Core.bundle.format("editor.patch", patch.name.isEmpty() ? "<unnamed>" : patch.name));
                    dialog.cont.top().pane(p -> {
                        p.top();
                        p.table(Styles.grayPanel, in -> {
                            in.add(patch.patch.replaceAll("\t", "  "), Styles.monoLabel).grow().wrap().left().labelAlign(Align.left);
                        }).margin(6f).growX().pad(5f).row();
                    }).grow();
                    dialog.addCloseButton();
                    dialog.show();
                });

                patchButton.margin(10f);
                patchButton.getLabel().setAlignment(Align.left, Align.left);
                patchButton.setDisabled(!patch.enabled);

                Table disabledOverlay = new Table(Styles.black6);
                disabledOverlay.touchable = Touchable.disabled;
                disabledOverlay.visible(() -> !patch.enabled);

                row.stack(patchButton, disabledOverlay).size(mobile ? 390f : 450f, rowHeight);

                row.button(Icon.copy, Styles.graySquarei, Vars.iconMed, () -> {
                    Core.app.setClipboardText(DataPatcher.externalize(patch.patch));
                    ui.showInfoFade("@copied");
                }).size(rowHeight);

                if(patch.modifiable){
                    row.button(Icon.refresh, Styles.graySquarei, Vars.iconMed, () -> {
                        showImport(str -> addPatch(str, patches.indexOf(patch)));
                    }).size(rowHeight);
                }else{
                    row.add().size(rowHeight);
                }

                var enabledIcon = patch.enabled ? Icon.cancel : Icon.ok;
                row.button(enabledIcon, Styles.graySquarei, Vars.iconMed, () -> {
                    var resolved = resolvePatch(patch);
                    if(resolved == null){
                        int seeIndex = visiblePatches.indexOf(patch);
                        int insert = getPatchInsertIndex(seeIndex);
                        resolved = clonePatch(patch);
                        patches.insert(insert, resolved);
                    }

                    boolean enabled = resolved.enabled;
                    try{
                        resolved.enabled = !enabled;
                        state.patcher.applySets(patches);
                        setup();
                    }catch(Exception e){
                        try{
                            resolved.enabled = enabled;
                            state.patcher.applySets(patches);
                        }catch(Exception ignored){
                            resolved.enabled = enabled;
                        }
                        ui.showException("@editor.patches.importerror", e);
                    }
                }).update(b -> b.getStyle().imageUp = patch.enabled ? Icon.cancel : Icon.ok).size(rowHeight);

                if(patch.modifiable){
                    row.button(Icon.trash, Styles.graySquarei, iconMed, () -> {
                        ui.showConfirm("@editor.patches.delete.confirm",  () -> {
                            int index = patches.indexOf(patch);
                            if(index < 0){
                                var resolved = resolvePatch(patch);
                                if(resolved == null) return;
                                index = patches.indexOf(resolved);
                                if(index < 0) return;
                            }
                            var removed = patches.remove(index);
                            try{
                                state.patcher.applySets(patches);
                                setup();
                            }catch(Exception e){
                                boolean restored = false;
                                try{
                                    patches.insert(index, removed);
                                    restored = true;
                                    state.patcher.applySets(patches);
                                }catch(Exception ignored){
                                    if(!restored){
                                        patches.insert(index, removed);
                                    }
                                }
                                ui.showException("@editor.patches.importerror", e);
                            }
                        });
                    }).size(rowHeight);
                }else{
                    row.add().size(rowHeight);
                }

                patchRows.add(row);
                t.add(row).left().row();
            }
        }
    }

    private void addRuntimePatch(Seq<DataPatcher.PatchSet> visiblePatches, String path){
        var file = Vars.tree.get(path);
        if(!file.exists()) return;

        String patchText = file.readString();
        if(visiblePatches.contains(p -> p.patch.equals(patchText))) return;

        JsonValue json = new JsonValue("error");
        try{
            json = new Json().fromJson(null, Jval.read(patchText).toString(Jval.Jformat.plain));
        }catch(Throwable ignored){
        }

        var patch = new DataPatcher.PatchSet(patchText, json);
        patch.modifiable = patch.persistSave = false;
        patch.enabled = false;
        if(json.isObject()){
            patch.name = json.getString("name", "");
        }
        visiblePatches.add(patch);
    }

    private void dragReorder(Button moveButton, DataPatcher.PatchSet patch){
        moveButton.addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(button != KeyCode.mouseLeft || pointer > 0) return false;

                dragPatch = patch;
                dragPointer = pointer;
                dragEnabled = !mobile;
                dragMoved = false;
                dragY = moveButton.localToStageCoordinates(Tmp.v1.set(x, y)).y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer){
                if(pointer != dragPointer || dragPatch != patch || !dragEnabled) return;

                float stageY = moveButton.localToStageCoordinates(Tmp.v1.set(x, y)).y;
                if(Math.abs(stageY - dragY) >= Scl.scl(4f)){
                    dragMoved = true;
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(pointer != dragPointer || dragPatch != patch) return;

                if(dragEnabled && dragMoved){
                    finishDragReorder(moveButton.localToStageCoordinates(Tmp.v1.set(x, y)).y);
                }
                clearDragState();
            }
        });

        if(mobile){
            moveButton.addListener(new ElementGestureListener(20, 0.4f, 0.43f, 0.15f){
                @Override
                public boolean longPress(Element element, float x, float y){
                    if(dragPatch != patch) return false;
                    dragEnabled = true;
                    dragMoved = false;
                    dragY = moveButton.localToStageCoordinates(Tmp.v1.set(x, y)).y;
                    for(var listener : moveButton.getListeners()){
                        if(listener instanceof ClickListener cl){
                            cl.cancel();
                        }
                    }
                    return true;
                }
            });
        }
    }

    private void clearDragState(){
        dragPatch = null;
        dragPointer = -1;
        dragEnabled = dragMoved = false;
    }

    private int findDropIndex(float stageY){
        int best = -1;
        float bestDst = Float.MAX_VALUE;
        for(int i = 0; i < patchRows.size; i++){
            var row = patchRows.get(i);
            float rowY = row.localToStageCoordinates(Tmp.v1.set(0f, row.getHeight() / 2f)).y;
            float dst = Math.abs(stageY - rowY);
            if(dst < bestDst){
                bestDst = dst;
                best = i;
            }
        }
        return best;
    }

    private void finishDragReorder(float stageY){
        var patches = state.patcher.patches;
        int visible = findDropIndex(stageY);
        if(visible < 0) return;

        var moved = resolvePatch(dragPatch);
        if(moved == null){
            moved = clonePatch(dragPatch);
        }

        int from = patches.indexOf(moved);
        int to = getPatchInsertIndex(visible);
        if(from >= 0 && from == to) return;

        if(from >= 0){
            patches.remove(from);
            if(to > from) to--;
        }
        to = Math.min(Math.max(to, 0), patches.size);
        patches.insert(to, moved);
        try{
            state.patcher.applySets(patches);
            setup();
        }catch(Exception e){
            patches.remove(to);
            if(from >= 0){
                patches.insert(from, moved);
            }
            try{
                state.patcher.applySets(patches);
            }catch(Exception ignored){
                //keep restored order even if patching fails again
            }
            ui.showException("@editor.patches.importerror", e);
        }
    }

    private @Nullable DataPatcher.PatchSet resolvePatch(DataPatcher.PatchSet patch){
        var patches = state.patcher.patches;
        int index = patches.indexOf(patch);
        if(index >= 0) return patches.get(index);
        return patches.find(p -> p.patch.equals(patch.patch));
    }

    private int getPatchInsertIndex(int seeIndex){
        int count = 0;
        for(int i = 0; i < seeIndex && i < visiblePatches.size; i++){
            if(resolvePatch(visiblePatches.get(i)) != null){
                count++;
            }
        }
        return count;
    }

    private DataPatcher.PatchSet clonePatch(DataPatcher.PatchSet patch){
        var copy = new DataPatcher.PatchSet(patch.patch, patch.json);
        copy.name = patch.name;
        copy.enabled = patch.enabled;
        copy.modifiable = patch.modifiable;
        copy.persistSave = patch.persistSave;
        return copy;
    }

    void showImport(Cons<String> handler){
        BaseDialog dialog = new BaseDialog("@editor.import");
        dialog.cont.pane(p -> {
            p.margin(10f);
            p.table(Tex.button, t -> {
                TextButtonStyle style = Styles.flatt;
                t.defaults().size(280f, 60f).left();
                t.row();
                t.button("@schematic.copy.import", Icon.copy, style, () -> {
                    dialog.hide();
                    handler.get(Core.app.getClipboardText());
                }).marginLeft(12f).disabled(b -> Core.app.getClipboardText() == null);
                t.row();
                t.button("@schematic.importfile", Icon.download, style, () -> platform.showMultiFileChooser(file -> {
                    dialog.hide();
                    handler.get(file.readString());
                }, "json", "hjson", "json5")).marginLeft(12f);
                t.row();
            });
        });

        dialog.addCloseButton();
        dialog.show();
    }

    void addPatch(String patch){
        addPatch(patch, -1);
    }

    void addPatch(String patch, int replaceIndex){
        try{
            Jval.read(patch); //validation
            Seq<DataPatcher.PatchSet> patches = state.patcher.patches;
            var next = new DataPatcher.PatchSet(patch);
            int insertIndex = -1;
            DataPatcher.PatchSet prev = null;
            if(replaceIndex == -1){
                patches.add(next);
                insertIndex = patches.size - 1;
            }else{
                prev = patches.get(replaceIndex);
                next.enabled = prev.enabled;
                next.modifiable = prev.modifiable;
                next.persistSave = prev.persistSave;
                patches.set(replaceIndex, next);
            }
            try{
                state.patcher.applySets(patches);
            }catch(Exception e){
                if(replaceIndex == -1){
                    patches.remove(insertIndex);
                }else{
                    patches.set(replaceIndex, prev);
                }
                state.patcher.applySets(patches);
                throw e;
            }

            setup();
        }catch(Exception e){
            ui.showException("@editor.patches.importerror", e);
        }
    }

    int countFields(JsonValue value){
        if(value.isObject() || value.isArray()){
            int sum = 0;
            for(var child : value){
                sum += countFields(child);
            }
            return Math.max(sum, 1);
        }else{
            return 1;
        }
    }
}
