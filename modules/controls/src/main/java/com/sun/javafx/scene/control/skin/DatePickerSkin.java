/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javafx.scene.control.skin;

// Note: The TextField code is in sync with ComboBoxListViewSkin 3016:92d1f5d6c31a

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.chrono.HijrahChronology;
import java.time.format.DateTimeParseException;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
// import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.StringConverter;

import com.sun.javafx.scene.control.behavior.DatePickerBehavior;

public class DatePickerSkin extends ComboBoxPopupControl<LocalDate> {

    private DatePicker datePicker;
    private TextField displayNode;
    private DatePickerContent datePickerContent;
    private TextField textField;

    public DatePickerSkin(final DatePicker datePicker) {
        super(datePicker, new DatePickerBehavior(datePicker));

        this.datePicker = datePicker;
        this.textField = getEditableInputNode();

        if (arrowButton.getOnMouseReleased() == null) {
            arrowButton.setOnMouseReleased(new EventHandler<MouseEvent>() {
                @Override public void handle(MouseEvent e) {
                    ((DatePickerBehavior)getBehavior()).mouseReleased(e, true);
                    e.consume();
                }
            });
        }

        // move focus in to the textfield if the comboBox is editable
        datePicker.focusedProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean hasFocus) {
                if (datePicker.isEditable() && hasFocus) {
                    Platform.runLater(new Runnable() {
                        @Override public void run() {
                            textField.requestFocus();
                        }
                    });
                }
            }
        });

        datePicker.addEventFilter(InputEvent.ANY, new EventHandler<InputEvent>() {
            @Override public void handle(InputEvent t) {
                if (textField == null) return;

                // When the user hits the enter or F4 keys, we respond before
                // ever giving the event to the TextField.
                if (t instanceof KeyEvent) {
                    KeyEvent ke = (KeyEvent)t;

                    if (ke.getCode() == KeyCode.ENTER) {
                        setTextFromTextFieldIntoComboBoxValue();
                        /*
                        ** don't consume this if we're on an embedded
                        ** platform that supports 5-button navigation
                        */
                        if (!Utils.isTwoLevelFocus()) {
                            t.consume();
                        }
                        return;
                    } else if (ke.getCode() == KeyCode.F4 && ke.getEventType() == KeyEvent.KEY_RELEASED) {
                        if (datePicker.isShowing()) {
                            datePicker.hide();
                        } else {
                            datePicker.show();
                        }
                        t.consume();
                        return;
                    } else if (ke.getCode() == KeyCode.F10 || ke.getCode() == KeyCode.ESCAPE) {
                        // RT-23275: The TextField fires F10 and ESCAPE key events
                        // up to the parent, which are then fired back at the
                        // TextField, and this ends up in an infinite loop until
                        // the stack overflows. So, here we consume these two
                        // events and stop them from going any further.
                        t.consume();
                        return;
                    }
                }
            }
        });

        registerChangeListener(datePicker.chronologyProperty(), "CHRONOLOGY");
        registerChangeListener(datePicker.converterProperty(), "CONVERTER");
        registerChangeListener(datePicker.dayCellFactoryProperty(), "DAY_CELL_FACTORY");
        registerChangeListener(datePicker.showWeekNumbersProperty(), "SHOW_WEEK_NUMBERS");
        registerChangeListener(datePicker.valueProperty(), "VALUE");
    }

    @Override public Node getPopupContent() {
        if (datePickerContent == null) {
            if (datePicker.getChronology() instanceof HijrahChronology) {
                datePickerContent = new DatePickerHijrahContent(datePicker);
            } else {
                datePickerContent = new DatePickerContent(datePicker);
            }
        }

        return datePickerContent;
    }

    @Override protected void focusLost() {
        // do nothing
    }


    @Override public void show() {
        super.show();
        datePickerContent.clearFocus();
    }

    @Override protected void handleControlPropertyChanged(String p) {
        super.handleControlPropertyChanged(p);

        if ("CHRONOLOGY".equals(p) ||
            "DAY_CELL_FACTORY".equals(p)) {

            updateDisplayNode();
//             if (datePickerContent != null) {
//                 datePickerContent.refresh();
//             }
            datePickerContent = null;
            popup = null;
        } else if ("EDITOR".equals(p)) {
            getEditableInputNode();
        } else if ("SHOWING".equals(p)) {
            if (datePicker.isShowing()) {
                if (datePickerContent != null) {
                    LocalDate date = datePicker.getValue();
                    datePickerContent.displayedYearMonthProperty().set((date != null) ? YearMonth.from(date) : YearMonth.now());
                    datePickerContent.updateValues();
                }
                show();
            } else {
                hide();
            }
        } else if ("SHOW_WEEK_NUMBERS".equals(p)) {
            if (datePickerContent != null) {
                datePickerContent.updateGrid();
                datePickerContent.updateWeeknumberDateCells();
            }
        } else if ("VALUE".equals(p)) {
            updateDisplayNode();
            if (datePickerContent != null) {
                LocalDate date = datePicker.getValue();
                datePickerContent.displayedYearMonthProperty().set((date != null) ? YearMonth.from(date) : YearMonth.now());
                datePickerContent.updateValues();
            }
        }
    }

    @Override public Node getDisplayNode() {
        if (displayNode == null) {
            displayNode = getEditableInputNode();
            displayNode.getStyleClass().add("date-picker-display-node");

//             if (displayNode.getOnMouseReleased() == null) {
//                 displayNode.setOnMouseReleased(new EventHandler<MouseEvent>() {
//                     @Override public void handle(MouseEvent e) {
//                         ((DatePickerBehavior)getBehavior()).mouseReleased(e, true);
//                     }
//                 });
//             }

            updateDisplayNode();
//             datePicker.addEventHandler(ActionEvent.ACTION, new EventHandler<ActionEvent>() {
//                 @Override public void handle(ActionEvent t) {
//                     updateDisplayNode();
//                 }
//             });

//             if (displayNode.getOnMouseReleased() == null) {
//                 displayNode.setOnMouseReleased(new EventHandler<MouseEvent>() {
//                     @Override public void handle(MouseEvent e) {
//                         ((DatePickerBehavior)getBehavior()).mouseReleased(e, false);
//                         e.consume();
//                     }
//                 });
//             }
        }
        textField.setEditable(datePicker.isEditable());

        return displayNode;
    }

    private TextField getEditableInputNode() {
        if (textField != null) return textField;

        textField = datePicker.getEditor();
        textField.setFocusTraversable(true);
        textField.promptTextProperty().bind(datePicker.promptTextProperty());

        textField.focusedProperty().addListener(new ChangeListener<Boolean>() {
            @Override public void changed(ObservableValue<? extends Boolean> ov, Boolean t, Boolean hasFocus) {
                if (!datePicker.isEditable()) return;

                // RT-21454 starts here
                if (! hasFocus) {
                    setTextFromTextFieldIntoComboBoxValue();
                    pseudoClassStateChanged(CONTAINS_FOCUS_PSEUDOCLASS_STATE, false);
                } else {
                    pseudoClassStateChanged(CONTAINS_FOCUS_PSEUDOCLASS_STATE, true);
                }
            }
        });

        return textField;
    }


    private void updateDisplayNode() {
        if (displayNode != null) {
            LocalDate date = datePicker.getValue();
            StringConverter<LocalDate> c = datePicker.getConverter();

            if (date != null && c != null) {
                displayNode.setText(c.toString(date));
            } else {
                displayNode.setText("");
            }
        }
    }

    private void setTextFromTextFieldIntoComboBoxValue() {
        StringConverter<LocalDate> c = datePicker.getConverter();
        if (c != null) {
            LocalDate oldValue = datePicker.getValue();
            LocalDate value = oldValue;
            String text = textField.getText();

            if (text == null || text.isEmpty()) {
                value = null;
            } else {
                try {
                    value = c.fromString(text);
                } catch (DateTimeParseException ex) {
                }
            }

            datePicker.setValue(value);
            updateDisplayNode();
        }
    }

    public void syncWithAutoUpdate() {
        if (!getPopup().isShowing() && datePicker.isShowing()) {
            // Popup was dismissed. Maybe user clicked outside or typed ESCAPE.
            // Make sure DatePicker button is in sync.
            datePicker.hide();
        }
    }



    /***************************************************************************
     *                                                                         *
     * Stylesheet Handling                                                     *
     *                                                                         *
     **************************************************************************/

    private static PseudoClass CONTAINS_FOCUS_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("contains-focus");
}