package com.everyarrow.dittoe.io.file.servers.localfile.reader.strategy;

import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.utility.CsvUtils;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.List;

public class CSVReadStrategy extends ReadStrategy {

    public CSVReadStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        super.init();
    }

    public Reader getFileReader() {
        try {
            return new FileReader(this.getReader().getServer()
                    .resolveResourcePath(this.getReader().getServer().getResource().getResourceName())
                    .toFile());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void read() throws ExecutionException {
        // Get the first observer if any exist
        com.everyarrow.dittoe.utility.DataReadObserver observer = this.getReader().getDataReadObservers().isEmpty() ? 
            null : this.getReader().getDataReadObservers().get(0);
        CsvUtils.read(getFileReader(), this.getReader().getServer().getResource().getFields(), 
            this.getReader().getServer().getResource().getResourceProperties(), getStageStats(), observer);
    }

    public void read(int limit) throws ExecutionException {
        // Get the first observer if any exist
        com.everyarrow.dittoe.utility.DataReadObserver observer = this.getReader().getDataReadObservers().isEmpty() ? 
            null : this.getReader().getDataReadObservers().get(0);
        CsvUtils.read(getFileReader(), this.getReader().getServer().getResource().getFields(), 
            this.getReader().getServer().getResource().getResourceProperties(), limit, getStageStats(), observer);
    }

    @Override
    public List<Long> getRange() throws ExecutionException {
        return CsvUtils.getRange(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties());
    }

    @Override
    public long getDataCount() throws ExecutionException {
        return CsvUtils.getDataCount(getFileReader(), this.getReader().getServer().getResource().getFields(), this.getReader().getServer().getResource().getResourceProperties());
    }

    @Override
    public int getAverageRowSize() throws ExecutionException {
        return CsvUtils.getAverageRowSize(getFileReader(), this.getReader().getServer().getResource().getFields());
    }

    @Override
    public List<Field> getFields() throws ExecutionException {
        return CsvUtils.getFields(getFileReader());
    }

    @Override
    public com.everyarrow.dittoe.io.file.FileReader getReader() {
        return (com.everyarrow.dittoe.io.file.FileReader) super.getReader();
    }
}
