package com.mantimetrics;

import com.mantimetrics.csv.CSVWriter;
import com.mantimetrics.git.GitService;
import com.mantimetrics.model.MethodData;
import com.mantimetrics.pmd.PmdAnalyzer;

import java.io.BufferedWriter;
import java.util.List;
import java.util.Map;

public final class ProjectContext {

    /* dati d’identità del progetto */
    public final String owner;
    public final String repo;

    /* servizi & dipendenze */
    public final GitService git;
    public final PmdAnalyzer pmd;
    public final CSVWriter csvOut;

    /* strutture condivise fra release */
    public final Map<String,List<String>> file2Keys;
    public final Map<String,MethodData>   prevData;
    public final List<String>             bugKeys;

    /* risorsa di output */
    public final BufferedWriter writer;

    private ProjectContext(Builder b) {
        this.owner     = b.owner;
        this.repo      = b.repo;
        this.git       = b.git;
        this.pmd       = b.pmd;
        this.csvOut    = b.csvOut;
        this.file2Keys = b.file2Keys;
        this.prevData  = b.prevData;
        this.bugKeys   = b.bugKeys;
        this.writer    = b.writer;
    }

    /* pattern builder per evitare costruttori mostruosi */
    public static class Builder {
        private String owner;
        private String repo;
        private GitService git;
        private PmdAnalyzer pmd;
        private CSVWriter csvOut;
        private Map<String,List<String>> file2Keys;
        private Map<String, MethodData>   prevData;
        private List<String>             bugKeys;
        private BufferedWriter writer;

        public Builder owner(String o){this.owner=o; return this;}
        public Builder repo(String r){this.repo=r; return this;}
        public Builder git(GitService g){this.git=g; return this;}
        public Builder pmd(PmdAnalyzer p){this.pmd=p; return this;}
        public Builder csvOut(CSVWriter c){this.csvOut=c; return this;}
        public Builder file2Keys(Map<String,List<String>> m){this.file2Keys=m; return this;}
        public Builder prevData(Map<String,MethodData> m){this.prevData=m; return this;}
        public Builder bugKeys(List<String> l){this.bugKeys=l; return this;}
        public Builder writer(BufferedWriter w){this.writer=w; return this;}

        public ProjectContext build(){ return new ProjectContext(this); }
    }
}