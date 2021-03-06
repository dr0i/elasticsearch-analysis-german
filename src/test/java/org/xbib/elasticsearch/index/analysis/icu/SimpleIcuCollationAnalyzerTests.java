package org.xbib.elasticsearch.index.analysis.icu;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.util.ULocale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.collation.ICUCollationKeyAnalyzer;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.inject.Injector;
import org.elasticsearch.common.inject.ModulesBuilder;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsModule;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.EnvironmentModule;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexNameModule;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.settings.IndexSettingsModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisModule;
import org.elasticsearch.indices.analysis.IndicesAnalysisService;
import org.junit.Test;
import org.xbib.elasticsearch.index.analysis.BaseTokenStreamTest;
import org.xbib.elasticsearch.index.analysis.HexDump;

import java.io.IOException;
import java.io.StringWriter;

public class SimpleIcuCollationAnalyzerTests extends BaseTokenStreamTest {

    /*
    * Turkish has some funny casing.
    * This test shows how you can solve this kind of thing easily with collation.
    * Instead of using LowerCaseFilter, use a turkish collator with primary strength.
    * Then things will sort and match correctly.
    */
    @Test
    public void testBasicUsage() throws Exception {
        Index index = new Index("test");
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("index.analysis.analyzer.myAnalyzer.type", "icu_collation")
                .put("index.analysis.analyzer.myAnalyzer.language", "tr")
                .put("index.analysis.analyzer.myAnalyzer.strength", "primary")
                .put("index.analysis.analyzer.myAnalyzer.decomposition", "canonical")
                .build();
        AnalysisService analysisService = createAnalysisService(index, settings);
        Analyzer analyzer = analysisService.analyzer("myAnalyzer").analyzer();
        TokenStream tsUpper = analyzer.tokenStream(null, "I WİLL USE TURKİSH CASING");
        BytesRef b1 = bytesFromTokenStream(tsUpper);
        TokenStream tsLower = analyzer.tokenStream(null, "ı will use turkish casıng");
        BytesRef b2 = bytesFromTokenStream(tsLower);
        assertTrue(compare(b1.bytes, b2.bytes) == 0);
    }

    /*
    * Test usage of the decomposition option for unicode normalization.
    */
    @Test
    public void testNormalization() throws IOException {
        Index index = new Index("test");
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("index.analysis.analyzer.myAnalyzer.type", "icu_collation")
                .put("index.analysis.analyzer.myAnalyzer.language", "tr")
                .put("index.analysis.analyzer.myAnalyzer.strength", "primary")
                .put("index.analysis.analyzer.myAnalyzer.decomposition", "canonical")
                .build();
        AnalysisService analysisService = createAnalysisService(index, settings);
        Analyzer analyzer = analysisService.analyzer("myAnalyzer").analyzer();
        TokenStream tsUpper = analyzer.tokenStream(null, "I W\u0049\u0307LL USE TURKİSH CASING");
        BytesRef b1 = bytesFromTokenStream(tsUpper);
        TokenStream tsLower = analyzer.tokenStream(null, "ı will use turkish casıng");
        BytesRef b2 = bytesFromTokenStream(tsLower);
        assertTrue(compare(b1.bytes, b2.bytes) == 0);
    }

    /*
    * Test secondary strength, for english case is not significant.
    */
    @Test
    public void testSecondaryStrength() throws IOException {
        Index index = new Index("test");
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("index.analysis.analyzer.myAnalyzer.type", "icu_collation")
                .put("index.analysis.analyzer.myAnalyzer.language", "en")
                .put("index.analysis.analyzer.myAnalyzer.strength", "secondary")
                .put("index.analysis.analyzer.myAnalyzer.decomposition", "no")
                .build();
        AnalysisService analysisService = createAnalysisService(index, settings);
        Analyzer analyzer = analysisService.analyzer("myAnalyzer").analyzer();
        TokenStream tsUpper = analyzer.tokenStream("content", "TESTING");
        BytesRef b1 = bytesFromTokenStream(tsUpper);
        TokenStream tsLower = analyzer.tokenStream("content", "testing");
        BytesRef b2 = bytesFromTokenStream(tsLower);
        assertTrue(compare(b1.bytes, b2.bytes) == 0);
    }

    /*
    * Setting alternate=shifted to shift whitespace, punctuation and symbols
    * to quaternary level
    */
    @Test
    public void testIgnorePunctuation() throws IOException {
        Index index = new Index("test");
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("index.analysis.analyzer.myAnalyzer.type", "icu_collation")
                .put("index.analysis.analyzer.myAnalyzer.language", "en")
                .put("index.analysis.analyzer.myAnalyzer.strength", "primary")
                .put("index.analysis.analyzer.myAnalyzer.alternate", "shifted")
                .build();
        AnalysisService analysisService = createAnalysisService(index, settings);
        Analyzer analyzer = analysisService.analyzer("myAnalyzer").analyzer();
        TokenStream tsPunctuation = analyzer.tokenStream("content", "foo-bar");
        BytesRef b1 = bytesFromTokenStream(tsPunctuation);
        TokenStream tsWithoutPunctuation = analyzer.tokenStream("content", "foo bar");
        BytesRef b2 = bytesFromTokenStream(tsWithoutPunctuation);
        assertTrue(compare(b1.bytes, b2.bytes) == 0);
    }

    /*
    * Setting alternate=shifted and variableTop to shift whitespace, but not
    * punctuation or symbols, to quaternary level
    */
    @Test
    public void testIgnoreWhitespace() throws IOException {
        Index index = new Index("test");
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("index.analysis.analyzer.myAnalyzer.type", "icu_collation")
                .put("index.analysis.analyzer.myAnalyzer.language", "en")
                .put("index.analysis.analyzer.myAnalyzer.strength", "primary")
                .put("index.analysis.analyzer.myAnalyzer.alternate", "shifted")
                .put("index.analysis.analyzer.myAnalyzer.variableTop", 4096) // SPACE
                .build();
        AnalysisService analysisService = createAnalysisService(index, settings);
        Analyzer analyzer = analysisService.analyzer("myAnalyzer").analyzer();
        TokenStream tsWithoutSpace = analyzer.tokenStream(null, "foobar");
        BytesRef b1 = bytesFromTokenStream(tsWithoutSpace);
        TokenStream tsWithSpace = analyzer.tokenStream(null, "foo bar");
        BytesRef b2 = bytesFromTokenStream(tsWithSpace);
        assertTrue(compare(b1.bytes, b2.bytes) == 0);

        // now assert that punctuation still matters: foo-bar < foo bar
        TokenStream tsWithPunctuation = analyzer.tokenStream(null, "foo-bar");
        BytesRef b3 = bytesFromTokenStream(tsWithPunctuation);
        assertTrue(compare(b3.bytes, b1.bytes) < 0);
    }

    /*
    * Setting numeric to encode digits with numeric value, so that
    * foobar-9 sorts before foobar-10
    */
    @Test
    public void testNumerics() throws IOException {
        Index index = new Index("test");
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("index.analysis.analyzer.myAnalyzer.type", "icu_collation")
                .put("index.analysis.analyzer.myAnalyzer.language", "en")
                .put("index.analysis.analyzer.myAnalyzer.numeric", true)
                .build();
        AnalysisService analysisService = createAnalysisService(index, settings);
        Analyzer analyzer = analysisService.analyzer("myAnalyzer").analyzer();
        TokenStream tsNine = analyzer.tokenStream(null, "foobar-9");
        BytesRef b1 = bytesFromTokenStream(tsNine);
        TokenStream tsTen = analyzer.tokenStream(null, "foobar-10");
        BytesRef b2 = bytesFromTokenStream(tsTen);
        assertTrue(compare(b1.bytes, b2.bytes) == -1);
    }

    /*
    * Setting caseLevel=true to create an additional case level between
    * secondary and tertiary
    */
    @Test
    public void testIgnoreAccentsButNotCase() throws IOException {
        Index index = new Index("test");
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("index.analysis.analyzer.myAnalyzer.type", "icu_collation")
                .put("index.analysis.analyzer.myAnalyzer.language", "en")
                .put("index.analysis.analyzer.myAnalyzer.strength", "primary")
                .put("index.analysis.analyzer.myAnalyzer.caseLevel", "true")
                .build();
        AnalysisService analysisService = createAnalysisService(index, settings);
        Analyzer analyzer = analysisService.analyzer("myAnalyzer").analyzer();

        String withAccents = "résumé";
        String withoutAccents = "resume";
        String withAccentsUpperCase = "Résumé";
        String withoutAccentsUpperCase = "Resume";

        TokenStream tsWithAccents = analyzer.tokenStream(null, withAccents);
        BytesRef b1 = bytesFromTokenStream(tsWithAccents);
        TokenStream tsWithoutAccents = analyzer.tokenStream(null, withoutAccents);
        BytesRef b2 = bytesFromTokenStream(tsWithoutAccents);
        assertTrue(compare(b1.bytes, b2.bytes) == 0);

        TokenStream tsWithAccentsUpperCase = analyzer.tokenStream(null, withAccentsUpperCase);
        BytesRef b3 = bytesFromTokenStream(tsWithAccentsUpperCase);
        TokenStream tsWithoutAccentsUpperCase = analyzer.tokenStream(null, withoutAccentsUpperCase);
        BytesRef b4 = bytesFromTokenStream(tsWithoutAccentsUpperCase);
        assertTrue(compare(b3.bytes, b4.bytes) == 0);

        // now assert that case still matters: resume < Resume
        TokenStream tsLower = analyzer.tokenStream(null, withoutAccents);
        BytesRef b5 = bytesFromTokenStream(tsLower);
        TokenStream tsUpper = analyzer.tokenStream(null, withoutAccentsUpperCase);
        BytesRef b6 = bytesFromTokenStream(tsUpper);
        assertTrue(compare(b5.bytes, b6.bytes) < 0);
    }

    /*
    * Setting caseFirst=upper to cause uppercase strings to sort
    * before lowercase ones.
    */
    @Test
    public void testUpperCaseFirst() throws IOException {
        Index index = new Index("test");
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("index.analysis.analyzer.myAnalyzer.type", "icu_collation")
                .put("index.analysis.analyzer.myAnalyzer.language", "en")
                .put("index.analysis.analyzer.myAnalyzer.strength", "tertiary")
                .put("index.analysis.analyzer.myAnalyzer.caseFirst", "upper")
                .build();
        AnalysisService analysisService = createAnalysisService(index, settings);
        Analyzer analyzer = analysisService.analyzer("myAnalyzer").analyzer();
        String lower = "resume";
        String upper = "Resume";
        TokenStream tsLower = analyzer.tokenStream(null, lower);
        BytesRef b1 = bytesFromTokenStream(tsLower);
        TokenStream tsUpper = analyzer.tokenStream(null, upper);
        BytesRef b2 = bytesFromTokenStream(tsUpper);
        assertTrue(compare(b2.bytes, b1.bytes) < 0);
    }

    /*
    * For german, you might want oe to sort and match with o umlaut.
    * This is not the default, but you can make a customized ruleset to do this.
    *
    * The default is DIN 5007-1, this shows how to tailor a collator to get DIN 5007-2 behavior.
    *  http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4423383
    */
    @Test
    public void testCustomRules() throws Exception {
        RuleBasedCollator baseCollator = (RuleBasedCollator) Collator.getInstance(new ULocale("de_DE"));
        String DIN5007_2_tailorings =
                "& ae , a\u0308 & AE , A\u0308& oe , o\u0308 & OE , O\u0308& ue , u\u0308 & UE , u\u0308";

        RuleBasedCollator tailoredCollator = new RuleBasedCollator(baseCollator.getRules() + DIN5007_2_tailorings);
        String tailoredRules = tailoredCollator.getRules();

        Index index = new Index("test");
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("index.analysis.analyzer.myAnalyzer.type", "icu_collation")
                .put("index.analysis.analyzer.myAnalyzer.rules", tailoredRules)
                .put("index.analysis.analyzer.myAnalyzer.strength", "primary")
                .build();
        AnalysisService analysisService = createAnalysisService(index, settings);
        Analyzer analyzer = analysisService.analyzer("myAnalyzer").analyzer();
        String germanUmlaut = "Töne";
        String germanExpandedUmlaut = "Toene";
        String germanBase = "Tone";
        TokenStream tsUmlaut = analyzer.tokenStream(null, germanUmlaut);
        BytesRef b1 = bytesFromTokenStream(tsUmlaut);
        TokenStream tsExpanded = analyzer.tokenStream(null, germanExpandedUmlaut);
        BytesRef b2 = bytesFromTokenStream(tsExpanded);
        TokenStream tsBase = analyzer.tokenStream(null, germanBase);
        BytesRef b3 = bytesFromTokenStream(tsBase);
        assertTrue(compare(b1.bytes, b2.bytes) == 0);
        StringWriter w = new StringWriter();
        HexDump.dump(b2.bytes, w);
        System.err.println("b2="+w);
        StringWriter w2 = new StringWriter();
        HexDump.dump(b3.bytes, w2);
        System.err.println("b3="+w2);
//        assertTrue(compare(b2.bytes, b3.bytes) == 0);
    }

    private AnalysisService createAnalysisService(Index index, Settings settings) {
        Injector parentInjector = new ModulesBuilder().add(new SettingsModule(settings), new EnvironmentModule(new Environment(settings)), new IndicesAnalysisModule()).createInjector();
        Injector injector = new ModulesBuilder().add(
                new IndexSettingsModule(index, settings),
                new IndexNameModule(index),
                new AnalysisModule(settings, parentInjector.getInstance(IndicesAnalysisService.class))
                        .addProcessor(new IcuAnalysisBinderProcessor()))
                .createChildInjector(parentInjector);

        return injector.getInstance(AnalysisService.class);
    }

    private BytesRef bytesFromTokenStream(TokenStream stream) throws IOException {
        TermToBytesRefAttribute termAttr = stream.getAttribute(TermToBytesRefAttribute.class);
        BytesRef bytesRef = termAttr.getBytesRef();
        stream.reset();
        while (stream.incrementToken()) {
            termAttr.fillBytesRef();
        }
        stream.close();
        BytesRef copy = new BytesRef();
        copy.copyBytes(bytesRef);
        return copy;
    }

    private int compare(byte[] left, byte[] right) {
        for (int i = 0, j = 0; i < left.length && j < right.length; i++, j++) {
            int a = (left[i] & 0xff);
            int b = (right[j] & 0xff);
            if (a != b) {
                return a - b;
            }
        }
        return left.length - right.length;
    }
}
