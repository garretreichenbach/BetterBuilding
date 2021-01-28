package net.thederpgamer.betterbuilding.gui.advancedbuildmode;

import net.thederpgamer.betterbuilding.BetterBuilding;
import org.schema.game.client.controller.manager.ingame.SymmetryPlanes;
import org.schema.game.client.view.gui.advanced.AdvancedGUIElement;
import org.schema.game.client.view.gui.advanced.tools.*;
import org.schema.game.client.view.gui.advancedbuildmode.AdvancedBuildModeSymmetry;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDockableDirtyInterface;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIHorizontalArea;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIInnerTextbox;
import java.util.ArrayList;

/**
 * NewAdvancedBuildModeSymmetry.java
 * Improved version of AdvancedBuildModeSymmetry that supports multiple planes of the same axis
 * ==================================================
 * Created 01/27/2021
 * @author TheDerpGamer
 */
public class NewAdvancedBuildModeSymmetry extends AdvancedBuildModeSymmetry {

    private ArrayList<GUIInnerTextbox> symmetryRows = new ArrayList<>();
    private ArrayList<SymmetryPlanes> symmetryPlanesList = new ArrayList<>();
    private boolean mirrorCubic = true;
    private boolean mirrorNonCubic = true;
    private boolean toggleAllSymmetry = true;

    public NewAdvancedBuildModeSymmetry(AdvancedGUIElement guiElement) {
        super(guiElement);
    }

    @Override
    public void build(final GUIContentPane contentPane, GUIDockableDirtyInterface dockableInterface) {
        contentPane.setTextBoxHeightLast(70);
        addButton(contentPane.getContent(0), 0, 0, new ButtonResult() {
            @Override
            public GUIHorizontalArea.HButtonColor getColor() {
                return GUIHorizontalArea.HButtonColor.YELLOW;
            }

            @Override
            public ButtonCallback initCallback() {
                return new ButtonCallback() {
                    @Override
                    public void pressedLeftMouse() {
                        if(symmetryRows.size() <= BetterBuilding.getInstance().maxSymmetryPlanes) addRow(symmetryRows.size(), contentPane.addNewTextBox(50));
                    }

                    @Override
                    public void pressedRightMouse() { }
                };
            }

            @Override
            public String getName() {
                return "ADD";
            }
        });
        addButton(contentPane.getContent(0), 1, 0, new ButtonResult() {
            @Override
            public GUIHorizontalArea.HButtonColor getColor() {
                return GUIHorizontalArea.HButtonColor.ORANGE;
            }

            @Override
            public ButtonCallback initCallback() {
                return new ButtonCallback() {
                    @Override
                    public void pressedLeftMouse() {
                        if(symmetryRows.size() > 1) removeRow();
                    }

                    @Override
                    public void pressedRightMouse() { }
                };
            }

            @Override
            public String getName() {
                return "REMOVE";
            }
        });
        addButton(contentPane.getContent(0), 2, 0, new ButtonResult() {
            @Override
            public GUIHorizontalArea.HButtonColor getColor() {
                return GUIHorizontalArea.HButtonColor.PINK;
            }

            @Override
            public ButtonCallback initCallback() {
                return new ButtonCallback() {
                    @Override
                    public void pressedLeftMouse() {
                        toggleAllSymmetry();
                    }

                    @Override
                    public void pressedRightMouse() { }
                };
            }

            @Override
            public String getName() {
                return "TOGGLE";
            }
        });
        addCheckbox(contentPane.getContent(0), 0, 0, new CheckboxResult() {

            @Override
            public CheckboxCallback initCallback() {
                return null;
            }

            @Override
            public String getName() {
                return Lng.str("Mirror cubic blocks");
            }

            @Override
            public boolean getDefault() {
                return mirrorCubic;
            }

            @Override
            public boolean getCurrentValue() {
                return mirrorCubic;
            }

            @Override
            public void setCurrentValue(boolean b) {
                setMirrorCubic(b);
            }
            @Override
            public String getToolTipText() {
                return Lng.str("Rotates cubic blocks on the other side of the plane\nto mirror the blocks you place");
            }
        });
        addCheckbox(contentPane.getContent(0), 0, 1, new CheckboxResult() {

            @Override
            public CheckboxCallback initCallback() {
                return null;
            }

            @Override
            public String getName() {
                return Lng.str("Mirror non-cubic blocks");
            }

            @Override
            public boolean getDefault() {
                return mirrorNonCubic;
            }

            @Override
            public boolean getCurrentValue() {
                return mirrorNonCubic;
            }

            @Override
            public void setCurrentValue(boolean b) {
                setMirrorNonCubic(b);
            }
            @Override
            public String getToolTipText() {
                return Lng.str("Rotates non-cubic blocks on the other side of the plane\nto mirror the blocks you place");
            }
        });
        GUIInnerTextbox textBox = contentPane.addNewTextBox(50);
        addRow(0, textBox);
    }

    private void setMirrorCubic(boolean bool) {
        this.mirrorCubic = bool;
        for(SymmetryPlanes plane : symmetryPlanesList) {
            plane.setMirrorCubeShapes(mirrorCubic);
        }
    }

    private void setMirrorNonCubic(boolean bool) {
        this.mirrorNonCubic = bool;
        for(SymmetryPlanes plane : symmetryPlanesList) {
            plane.setMirrorNonCubicShapes(mirrorNonCubic);
        }
    }

    private void toggleAllSymmetry() {
        toggleAllSymmetry = !toggleAllSymmetry;
        for(SymmetryPlanes plane : symmetryPlanesList) {
            //Todo
        }
    }

    private void addRow(final int index, GUIInnerTextbox textBox) {
        addButton(textBox, 0, 0, new NewSymmetryResult(index, SymmetryPlanes.MODE_XY));
        addButton(textBox, 1, 0, new NewSymmetryResult(index + 1, SymmetryPlanes.MODE_XZ));
        addButton(textBox, 2, 0, new NewSymmetryResult(index + 2, SymmetryPlanes.MODE_YZ));

        addButton(textBox, 0, 1, new ButtonResult() {
            @Override
            public GUIHorizontalArea.HButtonColor getColor() {
                return GUIHorizontalArea.HButtonColor.BLUE;
            }

            @Override
            public ButtonCallback initCallback() {
                return new ButtonCallback() {
                    @Override
                    public void pressedLeftMouse() {
                        int value = symmetryPlanesList.get(index).getXyExtraDist();
                        symmetryPlanesList.get(index).setXyExtraDist(value == 0 ? 1 : 0);
                    }

                    @Override
                    public void pressedRightMouse() {
                    }

                };

            }

            @Override
            public String getName() {
                return Lng.str("XY [" + (index + 1) + "] ODD");
            }

            @Override
            public boolean isHighlighted() {
                return symmetryPlanesList.get(index).getXyExtraDist() == 1;
            }
        });
        addButton(textBox, 1, 1, new ButtonResult() {
            @Override
            public GUIHorizontalArea.HButtonColor getColor() {
                return GUIHorizontalArea.HButtonColor.GREEN;
            }

            @Override
            public ButtonCallback initCallback() {
                return new ButtonCallback() {
                    @Override
                    public void pressedLeftMouse() {
                        int value = symmetryPlanesList.get(index).getXzExtraDist();
                        symmetryPlanesList.get(index).setXzExtraDist(value == 0 ? 1 : 0);
                    }

                    @Override
                    public void pressedRightMouse() {
                    }

                };

            }

            @Override
            public String getName() {
                return Lng.str("XZ [" + (index + 1) + "] ODD");
            }

            @Override
            public boolean isHighlighted() {
                return symmetryPlanesList.get(index).getXzExtraDist() == 1;
            }
        });
        addButton(textBox, 2, 1, new ButtonResult() {
            @Override
            public GUIHorizontalArea.HButtonColor getColor() {
                return GUIHorizontalArea.HButtonColor.RED;
            }

            @Override
            public ButtonCallback initCallback() {
                return new ButtonCallback() {
                    @Override
                    public void pressedLeftMouse() {
                        int value = symmetryPlanesList.get(index).getYzExtraDist();
                        symmetryPlanesList.get(index).setYzExtraDist(value == 0 ? 1 : 0);
                    }

                    @Override
                    public void pressedRightMouse() {
                    }

                };
            }

            @Override
            public String getName() {
                return Lng.str("YZ [" + (index + 1) + "] ODD");
            }

            @Override
            public boolean isHighlighted() {
                return symmetryPlanesList.get(index).getYzExtraDist() == 1;
            }
        });
        symmetryRows.add(textBox);
    }

    private void removeRow() {
        removeElement(symmetryRows.get(symmetryRows.size() - 1), 0, symmetryRows.size() + 1);
        symmetryRows.remove(symmetryRows.size() - 1);
        symmetryPlanesList.remove(symmetryPlanesList.size() - 1);
        symmetryPlanesList.remove(symmetryPlanesList.size() - 1);
        symmetryPlanesList.remove(symmetryPlanesList.size() - 1);
    }

    @Override
    public GUIAdvButton addButton(GUIElement element, int x, int y, ButtonResult result) {
        GUIAdvButton button = new GUIAdvButton(getState(), element, result);
        addElement(button, element, x, y);
        return button;
    }

    private class NewSymmetryResult extends ButtonResult {

        private final int index;
        private final int mode;
        private SymmetryPlanes plane;

        public NewSymmetryResult(int index, int mode) {
            this.index = index;
            this.mode = mode;
            this.plane = new SymmetryPlanes();
            symmetryPlanesList.add(plane);
        }

        @Override
        public GUIHorizontalArea.HButtonColor getColor() {
            switch (mode) {
                case (SymmetryPlanes.MODE_XY):
                    return GUIHorizontalArea.HButtonColor.BLUE;
                case (SymmetryPlanes.MODE_XZ):
                    return GUIHorizontalArea.HButtonColor.GREEN;
                case (SymmetryPlanes.MODE_YZ):
                    return GUIHorizontalArea.HButtonColor.RED;
            }

            throw new RuntimeException("Mode fail: " + mode);
        }

        @Override
        public ButtonCallback initCallback() {
            return new ButtonCallback() {

                @Override
                public void pressedRightMouse() {
                    plane.setPlaceMode(0);
                }

                @Override
                public void pressedLeftMouse() {
                    if (plane.getPlaceMode() == 0) {
                        switch (mode) {
                            case (SymmetryPlanes.MODE_XY):
                                if (plane.isXyPlaneEnabled()) {
                                    plane.setXyPlaneEnabled(false);
                                } else {
                                    plane.setPlaceMode(mode);
                                }
                                break;
                            case (SymmetryPlanes.MODE_XZ):
                                if (plane.isXzPlaneEnabled()) {
                                    plane.setXzPlaneEnabled(false);
                                } else {
                                    plane.setPlaceMode(mode);
                                }
                                break;
                            case (SymmetryPlanes.MODE_YZ):
                                if (plane.isYzPlaneEnabled()) {
                                    plane.setYzPlaneEnabled(false);
                                } else {
                                    plane.setPlaceMode(mode);
                                }
                                break;
                        }
                    } else {
                        plane.setPlaceMode(0);
                    }

                }
            };
        }

        @Override
        public String getName() {
            if (plane.getPlaceMode() == mode) {
                return Lng.str("*click on block*");
            } else {
                switch (mode) {
                    case (SymmetryPlanes.MODE_XY):
                        if (plane.isXyPlaneEnabled()) {
                            return Lng.str("Unset XY");
                        } else {
                            return Lng.str("XY");
                        }
                    case (SymmetryPlanes.MODE_XZ):
                        if (plane.isXzPlaneEnabled()) {
                            return Lng.str("Unset XZ");
                        } else {
                            return Lng.str("XZ");
                        }
                    case (SymmetryPlanes.MODE_YZ):
                        if (plane.isYzPlaneEnabled()) {
                            return Lng.str("Unset YZ");
                        } else {
                            return Lng.str("YZ");
                        }
                }
                throw new RuntimeException("Mode fail: " + mode);
            }
        }

        @Override
        public boolean isHighlighted() {
            switch (mode) {
                case (SymmetryPlanes.MODE_XY):
                    if (plane.isXyPlaneEnabled()) {
                        return true;
                    }
                    break;
                case (SymmetryPlanes.MODE_XZ):
                    if (plane.isXzPlaneEnabled()) {
                        return true;
                    }
                    break;
                case (SymmetryPlanes.MODE_YZ):
                    if (plane.isYzPlaneEnabled()) {
                        return true;
                    }
                    break;
            }
            return false;
        }
    }
}
