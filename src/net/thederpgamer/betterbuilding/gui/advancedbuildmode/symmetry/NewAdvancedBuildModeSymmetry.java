package net.thederpgamer.betterbuilding.gui.advancedbuildmode.symmetry;

import net.thederpgamer.betterbuilding.BetterBuilding;
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

    private ArrayList<GUIInnerTextbox> xyBoxes = new ArrayList<>();
    private ArrayList<GUIInnerTextbox> xzBoxes = new ArrayList<>();
    private ArrayList<GUIInnerTextbox> yzBoxes = new ArrayList<>();

    private ArrayList<SymmetryPlane> xyPlanes = new ArrayList<>();
    private ArrayList<SymmetryPlane> xzPlanes = new ArrayList<>();
    private ArrayList<SymmetryPlane> yzPlanes = new ArrayList<>();

    private boolean mirrorCubic = true;
    private boolean mirrorNonCubic = true;

    public NewAdvancedBuildModeSymmetry(AdvancedGUIElement guiElement) {
        super(guiElement);
    }

    @Override
    public void build(final GUIContentPane contentPane, GUIDockableDirtyInterface dockableInterface) {
        contentPane.setTextBoxHeightLast(70);

        addButton(contentPane.getContent(0), 0, 0, new ButtonResult() {
            @Override
            public GUIHorizontalArea.HButtonColor getColor() {
                return GUIHorizontalArea.HButtonColor.BLUE;
            }

            @Override
            public ButtonCallback initCallback() {
                return new ButtonCallback() {
                    @Override
                    public void pressedLeftMouse() {
                        if(xyPlanes.size() <= BetterBuilding.getInstance().maxSymmetryPlanes) addRow(xyPlanes.size(), contentPane.addNewTextBox(50), 0);
                    }

                    @Override
                    public void pressedRightMouse() { }
                };
            }

            @Override
            public String getName() {
                return "ADD XY";
            }
        });
        addButton(contentPane.getContent(0), 1, 0, new ButtonResult() {
            @Override
            public GUIHorizontalArea.HButtonColor getColor() {
                return GUIHorizontalArea.HButtonColor.GREEN;
            }

            @Override
            public ButtonCallback initCallback() {
                return new ButtonCallback() {
                    @Override
                    public void pressedLeftMouse() {
                        if(xzPlanes.size() <= BetterBuilding.getInstance().maxSymmetryPlanes) addRow(xzPlanes.size(), contentPane.addNewTextBox(50), 1);
                    }

                    @Override
                    public void pressedRightMouse() { }
                };
            }

            @Override
            public String getName() {
                return "ADD XZ";
            }
        });
        addButton(contentPane.getContent(0), 2, 0, new ButtonResult() {
            @Override
            public GUIHorizontalArea.HButtonColor getColor() {
                return GUIHorizontalArea.HButtonColor.RED;
            }

            @Override
            public ButtonCallback initCallback() {
                return new ButtonCallback() {
                    @Override
                    public void pressedLeftMouse() {
                        if(yzPlanes.size() <= BetterBuilding.getInstance().maxSymmetryPlanes) addRow(yzPlanes.size(), contentPane.addNewTextBox(50), 2);
                    }

                    @Override
                    public void pressedRightMouse() { }
                };
            }

            @Override
            public String getName() {
                return "ADD YZ";
            }
        });

        addButton(contentPane.getContent(0), 0, 1, new ButtonResult() {
            @Override
            public GUIHorizontalArea.HButtonColor getColor() {
                return GUIHorizontalArea.HButtonColor.BLUE;
            }

            @Override
            public ButtonCallback initCallback() {
                return new ButtonCallback() {
                    @Override
                    public void pressedLeftMouse() {
                        if(xyPlanes.size() > 1) removeRow(0);
                    }

                    @Override
                    public void pressedRightMouse() { }
                };
            }

            @Override
            public String getName() {
                return "REMOVE XY";
            }
        });
        addButton(contentPane.getContent(0), 1, 1, new ButtonResult() {
            @Override
            public GUIHorizontalArea.HButtonColor getColor() {
                return GUIHorizontalArea.HButtonColor.GREEN;
            }

            @Override
            public ButtonCallback initCallback() {
                return new ButtonCallback() {
                    @Override
                    public void pressedLeftMouse() {
                        if(xzPlanes.size() > 1) removeRow(1);
                    }

                    @Override
                    public void pressedRightMouse() { }
                };
            }

            @Override
            public String getName() {
                return "REMOVE XZ";
            }
        });
        addButton(contentPane.getContent(0), 2, 1, new ButtonResult() {
            @Override
            public GUIHorizontalArea.HButtonColor getColor() {
                return GUIHorizontalArea.HButtonColor.RED;
            }

            @Override
            public ButtonCallback initCallback() {
                return new ButtonCallback() {
                    @Override
                    public void pressedLeftMouse() {
                        if(yzPlanes.size() > 1) removeRow(2);
                    }

                    @Override
                    public void pressedRightMouse() { }
                };
            }

            @Override
            public String getName() {
                return "REMOVE YZ";
            }
        });

        addCheckbox(contentPane.getContent(0), 0, 1, new CheckboxResult() {

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
        addCheckbox(contentPane.getContent(0), 0, 2, new CheckboxResult() {

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
        addRow(0, textBox, 0);
        addRow(0, textBox, 1);
        addRow(0, textBox, 2);
    }

    private ArrayList<SymmetryPlane> getAllPlanes() {
        ArrayList<SymmetryPlane> allPlanes = new ArrayList<>();
        allPlanes.addAll(xyPlanes);
        allPlanes.addAll(xzPlanes);
        allPlanes.addAll(yzPlanes);
        return allPlanes;
    }

    private void setMirrorCubic(boolean bool) {
        this.mirrorCubic = bool;
        for(SymmetryPlane plane : getAllPlanes()) {
            plane.setMirrorCubeShapes(mirrorCubic);
        }
    }

    private void setMirrorNonCubic(boolean bool) {
        this.mirrorNonCubic = bool;
        for(SymmetryPlane plane : getAllPlanes()) {
            plane.setMirrorNonCubicShapes(mirrorNonCubic);
        }
    }

    private void addRow(final int index, GUIInnerTextbox textBox, int type) {
        if(type == 0) {
            xyBoxes.add(textBox);
            addButton(textBox, 0, 0, new NewSymmetryResult(index, SymmetryMode.XY));
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
                            int value = xyPlanes.get(index).getExtraDist();
                            xyPlanes.get(index).setExtraDist(value == 0 ? 1 : 0);
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
                    return xyPlanes.get(index).getExtraDist() == 1;
                }
            });
        } else if(type == 1) {
            xzBoxes.add(textBox);
            addButton(textBox, 1, 0, new NewSymmetryResult(index, SymmetryMode.XZ));
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
                            int value = xzPlanes.get(index).getExtraDist();
                            xzPlanes.get(index).setExtraDist(value == 0 ? 1 : 0);
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
                    return xzPlanes.get(index).getExtraDist() == 1;
                }
            });
        } else if(type == 2) {
            yzBoxes.add(textBox);
            addButton(textBox, 2, 0, new NewSymmetryResult(index, SymmetryMode.YZ));
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
                            int value = yzPlanes.get(index).getExtraDist();
                            yzPlanes.get(index).setExtraDist(value == 0 ? 1 : 0);
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
                    return yzPlanes.get(index).getExtraDist() == 1;
                }
            });
        }

    }

    private void removeRow(int type) {
        if(type == 0) {
            removeElement(xyBoxes.get(xyBoxes.size() - 1), 0, xyBoxes.size());
            xyPlanes.remove(xyPlanes.size() - 1);
        } else if(type == 1) {
            removeElement(xzBoxes.get(xzBoxes.size() - 1), 1, xzBoxes.size());
            xzPlanes.remove(xzPlanes.size() - 1);
        } else if(type == 2) {
            removeElement(yzBoxes.get(yzBoxes.size() - 1), 2, yzBoxes.size());
            yzPlanes.remove(yzPlanes.size() - 1);
        }
    }

    @Override
    public GUIAdvButton addButton(GUIElement element, int x, int y, ButtonResult result) {
        GUIAdvButton button = new GUIAdvButton(getState(), element, result);
        addElement(button, element, x, y);
        return button;
    }

    private class NewSymmetryResult extends ButtonResult {

        private final int index;
        private SymmetryMode symmetryMode;
        private SymmetryPlane symmetryPlane;

        public NewSymmetryResult(int index, SymmetryMode symmetryMode) {
            this.index = index;
            this.symmetryMode = symmetryMode;
            this.symmetryPlane = new SymmetryPlane(symmetryMode);
        }

        @Override
        public GUIHorizontalArea.HButtonColor getColor() {
            switch (symmetryMode) {
                case XY:
                    return GUIHorizontalArea.HButtonColor.BLUE;
                case XZ:
                    return GUIHorizontalArea.HButtonColor.GREEN;
                case YZ:
                    return GUIHorizontalArea.HButtonColor.RED;
            }

            throw new RuntimeException("Mode fail: " + symmetryMode.name());
        }

        @Override
        public ButtonCallback initCallback() {
            return new ButtonCallback() {

                @Override
                public void pressedRightMouse() {
                    symmetryPlane.setPlaceMode(false);
                }

                @Override
                public void pressedLeftMouse() {
                    if (!symmetryPlane.inPlaceMode()) {
                        if(symmetryPlane.isEnabled()) {
                            symmetryPlane.setEnabled(false);
                        } else {
                            symmetryPlane.setPlaceMode(true);
                        }
                    } else {
                        symmetryPlane.setPlaceMode(false);
                    }

                }
            };
        }

        @Override
        public String getName() {
            if (symmetryPlane.inPlaceMode()) {
                return Lng.str("*click on block*");
            } else {
                switch(symmetryMode) {
                    case XY:
                        if (symmetryPlane.isEnabled()) {
                            return Lng.str("Unset XY");
                        } else {
                            return Lng.str("XY");
                        }
                    case XZ:
                        if (symmetryPlane.isEnabled()) {
                            return Lng.str("Unset XZ");
                        } else {
                            return Lng.str("XZ");
                        }
                    case YZ:
                        if (symmetryPlane.isEnabled()) {
                            return Lng.str("Unset YZ");
                        } else {
                            return Lng.str("YZ");
                        }
                }
                throw new RuntimeException("Mode fail: " + symmetryMode.name());
            }
        }

        @Override
        public boolean isHighlighted() {
            return symmetryPlane.isEnabled();
        }
    }
}
