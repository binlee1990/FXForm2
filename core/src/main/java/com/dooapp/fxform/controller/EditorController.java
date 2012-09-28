package com.dooapp.fxform.controller;

import com.dooapp.fxform.FXForm;
import com.dooapp.fxform.model.Element;
import com.dooapp.fxform.view.FXFormNode;
import javafx.beans.binding.ObjectBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created at 27/09/12 17:15.<br>
 *
 * @author Antoine Mischler <antoine@dooapp.com>
 */
public class EditorController extends NodeController {
    /**
     * The logger
     */
    private static final Logger logger = LoggerFactory.getLogger(EditorController.class);

    public EditorController(FXForm fxForm, Element element) {
        super(fxForm, element);
    }

    @Override
    protected void bind(FXFormNode fxFormNode) {
        fxFormNode.getProperty().bind(new ObjectBinding() {
            {
                bind(getElement());
            }

            @Override
            protected Object computeValue() {
                return getFxForm().getAdapter(getNode(), getElement()).adaptTo(getElement());
            }
        });
    }
}