package org.xbib.elasticsearch.index.analysis.icu;

import com.ibm.icu.text.FilteredNormalizer2;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.UnicodeSet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.icu.ICUFoldingFilter;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.settings.IndexSettings;


/**
 * Uses the {@link org.apache.lucene.analysis.icu.ICUFoldingFilter}.
 * Applies foldings from UTR#30 Character Foldings.
 * <p/>
 * Can be filtered to handle certain characters in a specified way (see http://icu-project.org/apiref/icu4j/com/ibm/icu/text/UnicodeSet.html)
 * E.g national chars that should be retained (filter : "[^åäöÅÄÖ]").
 * <p/>
 * <p>The <tt>unicodeSetFilter</tt> attribute can be used to provide the UniCodeSet for filtering.
 */
public class IcuFoldingTokenFilterFactory extends AbstractTokenFilterFactory {
    private final String unicodeSetFilter;

    @Inject
    public IcuFoldingTokenFilterFactory(Index index, @IndexSettings Settings indexSettings, @Assisted String name, @Assisted Settings settings) {
        super(index, indexSettings, name, settings);
        this.unicodeSetFilter = settings.get("unicodeSetFilter");
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        // The ICUFoldingFilter is in fact implemented as a ICUNormalizer2Filter.
        // ICUFoldingFilter lacks a constructor for adding filtering so we implemement it here
        if (unicodeSetFilter != null) {
            Normalizer2 base = Normalizer2.getInstance(
                    ICUFoldingFilter.class.getResourceAsStream("utr30.nrm"),
                    "utr30", Normalizer2.Mode.COMPOSE);
            UnicodeSet unicodeSet = new UnicodeSet(unicodeSetFilter);
            unicodeSet.freeze();
            Normalizer2 filtered = new FilteredNormalizer2(base, unicodeSet);
            return new org.apache.lucene.analysis.icu.ICUNormalizer2Filter(tokenStream, filtered);
        } else {
            return new ICUFoldingFilter(tokenStream);
        }
    }
}