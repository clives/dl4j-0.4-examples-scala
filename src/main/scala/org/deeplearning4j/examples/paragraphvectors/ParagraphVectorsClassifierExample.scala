package org.deeplearning4j.examples.paragraphvectors

import org.canova.api.util.ClassPathResource
import org.deeplearning4j.examples.paragraphvectors.tools.{FileLabelAwareIterator, LabelSeeker, MeansBuilder}
import org.deeplearning4j.models.embeddings.inmemory.InMemoryLookupTable
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors
import org.deeplearning4j.models.word2vec.VocabWord
import org.deeplearning4j.text.documentiterator.{LabelAwareIterator, LabelledDocument}
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor
import org.deeplearning4j.text.tokenization.tokenizerfactory.{DefaultTokenizerFactory, TokenizerFactory}
import org.nd4j.linalg.api.ndarray.INDArray
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/**
 * This is basic example for documents classification done with DL4j ParagraphVectors.
 * The overall idea is to use ParagraphVectors in the same way we use LDA: topic space modelling.
 *
 * In this example we assume we have few labeled categories that we can use for training, and few unlabeled documents. And our goal is to determine, which category these unlabeled documents fall into
 *
 *
 * Please note: This example could be improved by using learning cascade for higher accuracy, but that's beyond basic example paradigm.
 *
 * @author raver119@gmail.com
 */
object ParagraphVectorsClassifierExample {

    val log = LoggerFactory.getLogger(ParagraphVectorsClassifierExample.getClass)

    @throws[Exception]
    def main(args: Array[String]): Unit = {
        val resource = new ClassPathResource("paravec/labeled")

        // build a iterator for our dataset
        val iterator: LabelAwareIterator = new FileLabelAwareIterator.Builder()
                .addSourceFolder(resource.getFile)
                .build()

        val t: TokenizerFactory = new DefaultTokenizerFactory()
        t.setTokenPreProcessor(new CommonPreprocessor())

        // ParagraphVectors training configuration
        val paragraphVectors = new ParagraphVectors.Builder()
                .learningRate(0.025)
                .minLearningRate(0.001)
                .batchSize(1000)
                .epochs(20)
                .iterate(iterator)
                .trainWordVectors(true)
                .tokenizerFactory(t)
                .build()

        // Start model training
        paragraphVectors.fit()

        /*
         At this point we assume that we have model built and we can check, which categories our unlabeled document falls into
         So we'll start loading our unlabeled documents and checking them
        */

        val unlabeledResource = new ClassPathResource("paravec/unlabeled")

        val unlabeledIterator = new FileLabelAwareIterator.Builder()
                .addSourceFolder(unlabeledResource.getFile)
                .build()

        /*
         Now we'll iterate over unlabeled data, and check which label it could be assigned to

         Please note: for many domains it's normal to have 1 document fall into few labels at once, with different "weight" for each.
        */
        val lookupTable: InMemoryLookupTable[VocabWord] = paragraphVectors.getLookupTable.asInstanceOf
        val meansBuilder = new MeansBuilder(lookupTable, t)
        val seeker = new LabelSeeker(iterator.getLabelsSource.getLabels, lookupTable)

        while (unlabeledIterator.hasNextDocument()) {
            val document: LabelledDocument = unlabeledIterator.nextDocument()

            val documentAsCentroid: INDArray = meansBuilder.documentAsVector(document)
            val scores = seeker.getScores(documentAsCentroid)

            /*
             please note, document.getLabel() is used just to show which document we're looking at now, as a substitute for printing out the whole document itself.
             So, labels on these two documents are used like titles, just to visualize our classification done properly
            */
            log.info("Document '" + document.getLabel + "' falls into the following categories: ")
            scores.asScala.foreach { score =>
                log.info("        " + score.getFirst + ": " + score.getSecond)
            }

            /*
                Your output should be like this:

                Document 'health' falls into the following categories:
                    health: 0.29721372296220205
                    science: 0.011684473733853906
                    finance: -0.14755302887323793

                Document 'finance' falls into the following categories:
                    health: -0.17290237675941766
                    science: -0.09579267574606627
                    finance: 0.4460859189453788

                    so,now we know categories for yet unseen documents
             */
        }
    }
}
