package daqi;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class ResultHandler {

    public static void handleResult(File outputFile) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(DataFlowResult.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            DataFlowResult dataFlowResult = (DataFlowResult) unmarshaller.unmarshal(outputFile);

            filterDuplicatedResult(dataFlowResult);

            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(dataFlowResult, outputFile);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    private static void filterDuplicatedResult(DataFlowResult dataFlowResult) {
        if (dataFlowResult.results == null || dataFlowResult.results.size() == 0) return;
        List<DataFlowResult.Result> results = new LinkedList<>();
        for (DataFlowResult.Results resultsElement : dataFlowResult.results) {
            if (resultsElement != null) {
                for (DataFlowResult.Result resultElement : resultsElement.results) {
                    if (!listContains(results, resultElement)) results.add(resultElement);
                }
            }
        }
        DataFlowResult.Results outResults = new DataFlowResult.Results();
        outResults.results = results;
        dataFlowResult.results = Collections.singletonList(outResults);
    }

    private static boolean listContains(List list, Object target) {
        if (list == null || list.size() == 0) return false;
        for (Object o : list) {
            if (Objects.equals(o, target)) return true;
        }
        return false;
    }
}
