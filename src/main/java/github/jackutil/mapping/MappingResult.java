package github.jackutil.mapping;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MappingResult {
    private final ObjectNode output;
    private final List<String> errors;

    public MappingResult(ObjectNode output, List<String> errors) {
        this.output = output;
        this.errors = new ArrayList<>(errors);
    }

    public ObjectNode getOutput() {
        return output;
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}

