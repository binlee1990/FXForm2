/*
 * Copyright (c) 2011, dooApp <contact@dooapp.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of dooApp nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.dooapp.fxform;

import com.dooapp.fxform.adapter.Adapter;
import com.dooapp.fxform.controller.ElementController;
import com.dooapp.fxform.controller.PropertyElementController;
import com.dooapp.fxform.filter.FieldFilter;
import com.dooapp.fxform.filter.NonVisualFilter;
import com.dooapp.fxform.model.Element;
import com.dooapp.fxform.model.FormException;
import com.dooapp.fxform.model.PropertyElement;
import com.dooapp.fxform.model.impl.PropertyFieldElement;
import com.dooapp.fxform.model.impl.ReadOnlyPropertyFieldElement;
import com.dooapp.fxform.reflection.impl.ReflectionFieldProvider;
import com.dooapp.fxform.utils.ConfigurationStore;
import com.dooapp.fxform.view.FXFormNode;
import com.dooapp.fxform.view.factory.DefaultFactoryProvider;
import com.dooapp.fxform.view.factory.FactoryProvider;
import com.dooapp.fxform.view.factory.impl.AutoHidableLabelFactory;
import com.dooapp.fxform.view.factory.impl.LabelFactory;
import com.dooapp.fxform.view.skin.DefaultSkin;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * User: Antoine Mischler <antoine@dooapp.com>
 * Date: 09/04/11
 * Time: 21:26
 * The FXForm control
 */
public class FXForm<T> extends Control implements FormAPI<T> {

    private final static Logger logger = LoggerFactory.getLogger(FXForm.class);

    public static final String LABEL_ID_SUFFIX = "-form-label";

    public static final String LABEL_STYLE = "form-label";

    public static final String EDITOR_ID_SUFFIX = "-form-editor";

    public static final String EDITOR_STYLE = "form-editor";

    public static final String TOOLTIP_ID_SUFFIX = "-form-tooltip";

    public static final String TOOLTIP_STYLE = "form-tooltip";

    private final ObjectProperty<T> source = new SimpleObjectProperty<T>();

    private StringProperty title = new SimpleStringProperty();

    private final ObservableList<FieldFilter> filters = FXCollections.observableList(new LinkedList<FieldFilter>());

    private final ConfigurationStore<ElementController> controllers = new ConfigurationStore<ElementController>();

    private final ObjectProperty<ResourceBundle> resourceBundle = new SimpleObjectProperty<ResourceBundle>();

    private final ObjectProperty<FactoryProvider> editorFactoryProvider = new SimpleObjectProperty<FactoryProvider>();

    private final ObjectProperty<FactoryProvider> tooltipFactoryProvider = new SimpleObjectProperty<FactoryProvider>();

    private final ObjectProperty<FactoryProvider> labelFactoryProvider = new SimpleObjectProperty<FactoryProvider>();

    private final ObjectProperty<FactoryProvider> constraintFactoryProvider = new SimpleObjectProperty<FactoryProvider>();

    public void setTitle(String title) {
        this.title.set(title);
    }

    public FXForm() {
        this(new DefaultFactoryProvider());
    }

    public FXForm(T source) {
        this(source, new DefaultFactoryProvider());
    }

    public FXForm(FactoryProvider editorFactoryProvider) {
        this(null,
                new FactoryProvider() {
                    public Callback<Void, FXFormNode> getFactory(Element element) {
                        return new LabelFactory();
                    }
                }, new FactoryProvider() {
                    public Callback<Void, FXFormNode> getFactory(Element element) {
                        return new AutoHidableLabelFactory();
                    }
                }, editorFactoryProvider
        );
    }

    public FXForm(T source, FactoryProvider editorFactoryProvider) {
        this(source,
                new FactoryProvider() {
                    public Callback<Void, FXFormNode> getFactory(Element element) {
                        return new LabelFactory();
                    }
                }, new FactoryProvider() {
                    public Callback<Void, FXFormNode> getFactory(Element element) {
                        return new AutoHidableLabelFactory();
                    }
                }, editorFactoryProvider
        );
    }

    public FXForm(FactoryProvider labelFactoryProvider, FactoryProvider tooltipFactoryProvider, FactoryProvider editorFactoryProvider) {
        this(null, labelFactoryProvider, tooltipFactoryProvider, editorFactoryProvider);
    }

    public FXForm(T source, FactoryProvider labelFactoryProvider, FactoryProvider tooltipFactoryProvider, FactoryProvider editorFactoryProvider) {
        initBundle();

        this.source.addListener(new ChangeListener<T>() {
            public void changed(ObservableValue<? extends T> observableValue, T t, T t1) {
                if (t1 == null) {
                    dispose();
                } else if (controllers.isEmpty() || (t1.getClass() != t.getClass())) {
                    createControllers();
                }
            }
        });
        filters.add(new NonVisualFilter());
        filters.addListener(new ListChangeListener() {
            public void onChanged(Change change) {
                dispose();
                createControllers();
            }
        });
        this.setSkin(new DefaultSkin(this));
        setSource(source);
    }

    public void dispose() {
        for (ElementController controller : controllers) {
            clearBindings(controller);
            controller.dispose();
        }
        controllers.clear();
    }


    private void clearBindings(ElementController controller) {
        source.unbind();
    }

    private void createControllers() {
        if (source.get() == null)
            return;
        logger.debug("Creating controllers for " + source.get());
        controllers.clear();
        List<Field> fields = new ReflectionFieldProvider().getProperties(source.get());
        for (FieldFilter filter : filters) {
            fields = filter.filter(fields);
        }
        for (Field field : fields) {
            try {
                Element element = null;
                ElementController controller = null;
                if (Property.class.isAssignableFrom(field.getType())) {
                    element = new PropertyFieldElement(field);
                    ((PropertyFieldElement) element).sourceProperty().bind(source);
                    controller = new PropertyElementController(this, (PropertyElement) element);
                } else {
                    element = new ReadOnlyPropertyFieldElement(field);
                    ((ReadOnlyPropertyFieldElement) element).sourceProperty().bind(source);
                    controller = new ElementController(this, element);
                }
                if (element != null) {
                    controllers.add(controller);
                }
            } catch (FormException e) {
                logger.warn(e.getMessage(), e);
            }
        }
    }

    /**
     * Auto loading of default resource bundle and css file.
     */
    private void initBundle() {
        final StackTraceElement element = getCallingClass();
        String bundle = element.getClassName();
        if (resourceBundle.get() == null) {
            try {
                resourceBundle.set(ResourceBundle.getBundle(bundle));
                logger.debug("Default resource bundle loaded: " + bundle);
            } catch (MissingResourceException e) {
                logger.info("Default resource bundle not found: " + bundle);
            }
        }
        sceneProperty().addListener(new ChangeListener<Scene>() {
            public void changed(ObservableValue<? extends Scene> observableValue, Scene scene, Scene scene1) {
                URL css = FXForm.class.getResource(element.getFileName().substring(0, element.getFileName().indexOf(".")) + ".css");
                if (css != null && observableValue.getValue() != null) {
                    logger.debug("Registering " + css + " in " + observableValue.getValue());
                    getScene().getStylesheets().add(css.toExternalForm());
                }
            }
        });
    }

    /**
     * Retrieve the calling class in which the form is being created.
     *
     * @return the StackTraceElement representing the calling class
     */
    private StackTraceElement getCallingClass() {
        try {
            throw new Exception();
        } catch (Exception e) {
            int i = 1;
            while (e.getStackTrace()[i].getClassName().equals(FXForm.class.getName())) {
                i++;
            }
            return e.getStackTrace()[i];
        }
    }

    public StringProperty titleProperty() {
        return title;
    }

    public ObservableList<ElementController> getControllers() {
        return controllers;
    }

    public ConfigurationStore<ElementController> getStore() {
        return controllers;
    }

    public T getSource() {
        return source.get();
    }

    public void setSource(T source) {
        this.source.set(source);
    }

    public ObjectProperty<T> sourceProperty() {
        return source;
    }

    public ObservableList<FieldFilter> getFilters() {
        return filters;
    }

    public void addFilters(FieldFilter... filters) {
        this.filters.addAll(filters);
    }

    /**
     * Set the resource bundle used by this form to i18n labels, tooltips,...
     *
     * @param resourceBundle
     */
    public void setResourceBundle(ResourceBundle resourceBundle) {
        this.resourceBundle.set(resourceBundle);
    }

    public ObjectProperty<ResourceBundle> resourceBundleProperty() {
        return resourceBundle;
    }

    public ResourceBundle getResourceBundle() {
        return resourceBundle.get();
    }

    public FactoryProvider getEditorFactoryProvider() {
        return editorFactoryProvider.get();
    }

    public FactoryProvider getTooltipFactoryProvider() {
        return tooltipFactoryProvider.get();
    }

    public FactoryProvider getLabelFactoryProvider() {
        return labelFactoryProvider.get();
    }

    public FactoryProvider getConstraintFactoryProvider() {
        return constraintFactoryProvider.get();
    }

    public void setEditorFactoryProvider(FactoryProvider editorFactoryProvider1) {
        editorFactoryProvider.set(editorFactoryProvider1);
    }

    public void setLabelFactoryProvider(FactoryProvider labelFactoryProvider1) {
        labelFactoryProvider.set(labelFactoryProvider1);
    }

    public void setTooltipFactoryProvider(FactoryProvider tooltipFactoryProvider1) {
        tooltipFactoryProvider.set(tooltipFactoryProvider1);
    }

    public void setConstraintFactoryProvider(FactoryProvider constraintFactoryProvider1) {
        constraintFactoryProvider.set(constraintFactoryProvider1);
    }

    public ObjectProperty<FactoryProvider> editorFactoryProvider() {
        return editorFactoryProvider;
    }

    public ObjectProperty<FactoryProvider> labelFactoryProvider() {
        return labelFactoryProvider;
    }

    public ObjectProperty<FactoryProvider> tooltipFactoryProvider() {
        return tooltipFactoryProvider;
    }

    public ObjectProperty<FactoryProvider> constraintFactoryProvider() {
        return constraintFactoryProvider;
    }

    public Adapter getAdapter(FXFormNode node, Element element) {
        return null;
    }
}
