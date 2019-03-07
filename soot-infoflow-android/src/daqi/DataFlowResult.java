package daqi;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;
import java.util.Objects;

@XmlRootElement(name = "DataFlowResults")
public class DataFlowResult {

    public static class Results {
        @XmlElement(name = "Result")
        public List<Result> results;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Results) {
                Results r = (Results) obj;
                return listEquals(results, r.results);
            }
            return false;
        }
    }

    public static class Result {
        @XmlElement(name = "Sink")
        public Sink sink;

        @XmlElement(name = "Sources")
        public Sources sources;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Result) {
                Result r = (Result) obj;
                return Objects.equals(sink, r.sink)
                        && Objects.equals(sources, r.sources);
            }
            return false;
        }
    }

    public static class Sink {
        @XmlAttribute(name = "Statement")
        public String statement;

        @XmlAttribute(name = "Method")
        public String method;

        @XmlElement(name = "AccessPath")
        public AccessPath accessPath;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Sink) {
                Sink r = (Sink) obj;
                return Objects.equals(statement, r.statement)
                        && Objects.equals(method, r.method)
                        && Objects.equals(accessPath, r.accessPath);
            }
            return false;
        }
    }

    public static class Sources {
        @XmlElement(name = "Source")
        public List<Source> sources;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Sources) {
                Sources r = (Sources) obj;
                return listEquals(sources, r.sources);
            }
            return false;
        }
    }

    public static class Source {
        @XmlAttribute(name = "Statement")
        public String statement;

        @XmlAttribute(name = "Method")
        public String method;

        @XmlElement(name = "AccessPath")
        public AccessPath accessPath;

        @XmlElement(name = "TaintPath")
        public TaintPath taintPath;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Source) {
                Source r = (Source) obj;
                return Objects.equals(statement, r.statement)
                        && Objects.equals(method, r.method)
                        && Objects.equals(accessPath, r.accessPath)
                        && Objects.equals(taintPath, r.taintPath);
            }
            return false;
        }
    }

    public static class AccessPath {
        @XmlAttribute(name = "Value")
        public String value;

        @XmlAttribute(name = "Type")
        public String type;

        @XmlAttribute(name = "TaintSubFields")
        public boolean taintSubFields;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof AccessPath) {
                AccessPath r = (AccessPath) obj;
                return Objects.equals(value, r.value)
                        && Objects.equals(type, r.type)
                        && taintSubFields == r.taintSubFields;
            }
            return false;
        }
    }

    public static class TaintPath {
        @XmlElement(name = "PathElement")
        public List<PathElement> pathElements;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TaintPath) {
                TaintPath r = (TaintPath) obj;
                return listEquals(pathElements, r.pathElements);
            }
            return false;
        }
    }

    public static class PathElement {
        @XmlAttribute(name = "Statement")
        public String statement;

        @XmlAttribute(name = "Method")
        public String method;

        @XmlElement(name = "AccessPath")
        public AccessPath accessPath;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PathElement) {
                PathElement r = (PathElement) obj;
                return Objects.equals(statement, r.statement)
                        && Objects.equals(method, r.method)
                        && Objects.equals(accessPath, r.accessPath);
            }
            return false;
        }
    }

    @XmlElement(name = "Results")
    public List<Results> results;

    private static boolean listEquals(List l, List r) {
        if (l == r) return true;
        if (l != null) {
            int i = 0, c = 0;
            if (r == null || (c = l.size()) != r.size())
                return false;
            while (i < c) {
                if (!Objects.equals(l.get(i), r.get(i)))
                    return false;
                i++;
            }
            return true;
        }
        return false;
    }
}
