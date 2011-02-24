/*
 * Copyright 2008, 2009 Daniël de Kok
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.langkit.tagger.cli;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.langkit.tagger.corpus.BrownCorpusReader;
import org.langkit.tagger.corpus.CorpusReader;
import org.langkit.tagger.corpus.CorpusReaderException;
import org.langkit.tagger.corpus.CorpusSentenceHandler;
import org.langkit.tagger.corpus.TaggedWord;

public class Train {
	private static class TrainHandler implements
			CorpusSentenceHandler<TaggedWord> {
		private final Map<String, Map<String, Integer>> d_lexicon;
		private final Map<String, Integer> d_uniGrams;
		private final Map<String, Integer> d_biGrams;
		private final Map<String, Integer> d_triGrams;

		public TrainHandler() {
			d_lexicon = new HashMap<String, Map<String, Integer>>();
			d_uniGrams = new HashMap<String, Integer>();
			d_biGrams = new HashMap<String, Integer>();
			d_triGrams = new HashMap<String, Integer>();
		}

		public Map<String, Integer> biGrams() {
			return d_biGrams;
		}

		public void handleSentence(List<TaggedWord> sentence) {
			for (int i = 0; i < sentence.size(); ++i) {
				addLexiconEntry(sentence.get(i));
				addUniGram(sentence, i);
				if (i > 0)
					addBiGram(sentence, i);
				if (i > 1)
					addTriGram(sentence, i);
			}
		}

		public Map<String, Map<String, Integer>> lexicon() {
			return d_lexicon;
		}

		public Map<String, Integer> triGrams() {
			return d_triGrams;
		}

		public Map<String, Integer> uniGrams() {
			return d_uniGrams;
		}

		private void addLexiconEntry(TaggedWord taggedWord) {
			String word = taggedWord.word();
			String tag = taggedWord.tag();

			if (!d_lexicon.containsKey(word))
				d_lexicon.put(word, new HashMap<String, Integer>());

			if (!d_lexicon.get(word).containsKey(tag))
				d_lexicon.get(word).put(tag, 1);
			else
				d_lexicon.get(word).put(tag, d_lexicon.get(word).get(tag) + 1);
		}

		private void addUniGram(List<TaggedWord> sentence, int index) {
			String uniGram = sentence.get(index).tag();

			if (!d_uniGrams.containsKey(uniGram))
				d_uniGrams.put(uniGram, 1);
			else
				d_uniGrams.put(uniGram, d_uniGrams.get(uniGram) + 1);
		}

		private void addBiGram(List<TaggedWord> sentence, int index) {
			String biGram = sentence.get(index - 1).tag() + " "
					+ sentence.get(index).tag();

			if (!d_biGrams.containsKey(biGram))
				d_biGrams.put(biGram, 1);
			else
				d_biGrams.put(biGram, d_biGrams.get(biGram) + 1);
		}

		private void addTriGram(List<TaggedWord> sentence, int index) {
			String triGram = sentence.get(index - 2).tag() + " "
					+ sentence.get(index - 1).tag() + " "
					+ sentence.get(index).tag();

			if (!d_triGrams.containsKey(triGram))
				d_triGrams.put(triGram, 1);
			else
				d_triGrams.put(triGram, d_triGrams.get(triGram) + 1);
		}
	}

	private static void writeNGrams(Map<String, Integer> uniGrams,
			Map<String, Integer> biGrams, Map<String, Integer> triGrams,
			BufferedWriter writer) throws IOException {
		for (Entry<String, Integer> entry : uniGrams.entrySet())
			writer.write(entry.getKey() + " " + entry.getValue() + "\n");

		for (Entry<String, Integer> entry : biGrams.entrySet())
			writer.write(entry.getKey() + " " + entry.getValue() + "\n");

		for (Entry<String, Integer> entry : triGrams.entrySet())
			writer.write(entry.getKey() + " " + entry.getValue() + "\n");

		writer.flush();
	}

	private static void writeLexicon(Map<String, Map<String, Integer>> lexicon,
			BufferedWriter writer) throws IOException {
		for (Entry<String, Map<String, Integer>> wordEntry : lexicon.entrySet()) {
			String word = wordEntry.getKey();

			writer.write(word);

			for (Entry<String, Integer> tagEntry : lexicon.get(word).entrySet()) {
				writer.write(" ");
				writer.write(tagEntry.getKey());
				writer.write(" ");
				writer.write(tagEntry.getValue().toString());
			}

			writer.newLine();
		}

		writer.flush();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length != 3) {
			System.out.println("Train corpus lexicon ngrams");
			System.exit(1);
		}

		List<TaggedWord> startMarkers = new ArrayList<TaggedWord>();
		startMarkers.add(new TaggedWord("<START>", "<START>"));
		startMarkers.add(new TaggedWord("<START>", "<START>"));
		List<TaggedWord> endMarkers = new ArrayList<TaggedWord>();
		endMarkers.add(new TaggedWord("<END>", "<END>"));

		CorpusReader<TaggedWord> corpusReader = new BrownCorpusReader(
				startMarkers, endMarkers, true);

		TrainHandler trainHandler = new TrainHandler();
		corpusReader.addHandler(trainHandler);

		try {
			corpusReader.parse(new BufferedReader(new InputStreamReader(
					new FileInputStream(args[0]), "UTF8")));
		} catch (IOException e) {
			System.out.println("Could not read corpus!");
			e.printStackTrace();
			System.exit(1);
		} catch (CorpusReaderException e) {
			e.printStackTrace();
			System.exit(1);
		}

		try {
			writeLexicon(trainHandler.lexicon(), new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(args[1]),
							"UTF8")));
			writeNGrams(trainHandler.uniGrams(), trainHandler.biGrams(),
					trainHandler.triGrams(), new BufferedWriter(new FileWriter(
							args[2])));
		} catch (IOException e) {
			System.out.println("Could not write training data!");
			e.printStackTrace();
			System.exit(1);
		}
	}
}
