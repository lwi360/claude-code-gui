package io.github.feelhappy.ccaitoolkit.handler;

import io.github.feelhappy.ccaitoolkit.handler.core.BaseMessageHandler;
import io.github.feelhappy.ccaitoolkit.handler.core.HandlerContext;

import io.github.feelhappy.ccaitoolkit.handler.diff.DiffBrowserBridge;
import io.github.feelhappy.ccaitoolkit.handler.diff.DiffFileOperations;
import io.github.feelhappy.ccaitoolkit.handler.diff.DiffRequestDispatcher;
import io.github.feelhappy.ccaitoolkit.handler.diff.EditableDiffHandler;
import io.github.feelhappy.ccaitoolkit.handler.diff.InteractiveDiffMessageHandler;
import io.github.feelhappy.ccaitoolkit.handler.diff.RefreshFileHandler;
import io.github.feelhappy.ccaitoolkit.handler.diff.SimpleDiffDisplayHandler;
import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import java.util.List;

/**
 * Diff and file refresh message handler.
 */
public class DiffHandler extends BaseMessageHandler {

    private static final Logger LOG = Logger.getInstance(DiffHandler.class);
    private final DiffRequestDispatcher dispatcher;
    private final String[] supportedTypes;

    public DiffHandler(HandlerContext context) {
        super(context);
        Gson gson = new Gson();
        DiffBrowserBridge browserBridge = new DiffBrowserBridge(context, gson);
        DiffFileOperations fileOperations = new DiffFileOperations(context);
        this.dispatcher = new DiffRequestDispatcher(List.of(
                new RefreshFileHandler(context, gson, fileOperations),
                new SimpleDiffDisplayHandler(context, gson, fileOperations),
                new EditableDiffHandler(context, gson, browserBridge, fileOperations),
                new InteractiveDiffMessageHandler(context, gson, browserBridge, fileOperations)
        ));
        this.supportedTypes = dispatcher.getAllSupportedTypes();
    }

    @Override
    public String[] getSupportedTypes() {
        return supportedTypes;
    }

    @Override
    public boolean handle(String type, String content) {
        boolean handled = dispatcher.dispatch(type, content);
        if (!handled) {
            LOG.debug("No diff action handler registered for type: " + type);
        }
        return handled;
    }
}
