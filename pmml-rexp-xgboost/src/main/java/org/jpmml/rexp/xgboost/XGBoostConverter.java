/*
 * Copyright (c) 2015 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp.xgboost;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.VerificationField;
import org.dmg.pmml.mining.MiningModel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.Label;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.rexp.DecorationUtil;
import org.jpmml.rexp.ModelConverter;
import org.jpmml.rexp.RExpEncoder;
import org.jpmml.rexp.RFactorVector;
import org.jpmml.rexp.RGenericVector;
import org.jpmml.rexp.RIntegerVector;
import org.jpmml.rexp.RNumberVector;
import org.jpmml.rexp.RRaw;
import org.jpmml.rexp.RStringVector;
import org.jpmml.rexp.RVector;
import org.jpmml.xgboost.FeatureMap;
import org.jpmml.xgboost.HasXGBoostOptions;
import org.jpmml.xgboost.Learner;
import org.jpmml.xgboost.ObjFunction;
import org.jpmml.xgboost.XGBoostUtil;

public class XGBoostConverter extends ModelConverter<RGenericVector> {

	private Learner learner = null;

	private FeatureMap featureMap = null;

	private boolean compact = true;


	public XGBoostConverter(RGenericVector booster){
		super(booster);

		this.compact = getOption("compact", Boolean.TRUE);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector booster = getObject();

		RStringVector featureNames = booster.getStringElement("feature_names", false);
		RGenericVector schema = booster.getGenericElement("schema", false);

		FeatureMap featureMap = ensureFeatureMap();

		if(featureNames != null){
			checkFeatureMap(featureMap, featureNames);
		}

		Learner learner = ensureLearner();

		ObjFunction obj = learner.obj();

		String targetField = "_target";
		List<String> targetCategories = null;

		if(schema != null){
			RStringVector responseName = schema.getStringElement("response_name", false);
			RStringVector responseLevels = schema.getStringElement("response_levels", false);

			if(responseName != null){
				targetField = responseName.asScalar();
			} // End if

			if(responseLevels != null){
				targetCategories = responseLevels.getValues();
			}
		}

		Label label = obj.encodeLabel(targetField, targetCategories, encoder);

		encoder.setLabel(label);

		List<Feature> features = featureMap.encodeFeatures(learner, encoder);
		for(Feature feature : features){
			encoder.addFeature(feature);
		}
	}

	@Override
	public MiningModel encodeModel(Schema schema){
		RGenericVector booster = getObject();

		RNumberVector<?> ntreeLimit = booster.getNumericElement("ntreelimit", false);
		RGenericVector boosterSchema = booster.getGenericElement("schema", false);

		RNumberVector<?> missing = boosterSchema.getNumericElement("missing", false);

		Learner learner = ensureLearner();

		Map<String, Object> options = new LinkedHashMap<>();
		options.put(HasXGBoostOptions.OPTION_MISSING, missing != null ? missing.asScalar() : null);
		options.put(HasXGBoostOptions.OPTION_COMPACT, this.compact);
		options.put(HasXGBoostOptions.OPTION_NUMERIC, true);
		options.put(HasXGBoostOptions.OPTION_NTREE_LIMIT, ntreeLimit != null ? ValueUtil.asInteger(ntreeLimit.asScalar()) : null);

		Schema xgbSchema = learner.toXGBoostSchema(schema);

		return learner.encodeModel(options, xgbSchema);
	}

	@Override
	protected Map<VerificationField, List<?>> encodeActiveValues(RGenericVector dataFrame){
		FeatureMap featureMap = ensureFeatureMap();

		checkFeatureMap(featureMap, dataFrame);

		List<FeatureMap.Entry> entries = featureMap.getEntries();

		Map<String, RVector<?>> data = new LinkedHashMap<>();

		for(int i = 0; i < dataFrame.size(); i++){
			FeatureMap.Entry entry = entries.get(i);
			RVector<?> column = dataFrame.getVectorValue(i);

			String name = entry.getName();

			FeatureMap.Entry.Type type = entry.getType();
			switch(type){
				case INDICATOR:
					{
						FeatureMap.IndicatorEntry indicatorEntry = (FeatureMap.IndicatorEntry)entry;

						RFactorVector factorColumn = (RFactorVector)data.get(name);
						if(factorColumn == null){
							factorColumn = new RFactorVector(null, null){

								private List<String> factorValues = new ArrayList<>();

								{
									for(int i = 0; i < column.size(); i++){
										this.factorValues.add(null);
									}
								}

								@Override
								public List<String> getFactorValues(){
									return this.factorValues;
								}
							};

							data.put(name, factorColumn);
						}

						List<String> factorValues = factorColumn.getFactorValues();

						List<? extends Number> mask = (List)column.getValues();

						for(int row = 0; row < mask.size(); row++){
							Number rowMask = mask.get(row);

							if(rowMask != null && rowMask.doubleValue() == 1d){
								String value = indicatorEntry.getValue();

								// XXX
								if(value == null){
									value = "true";
								}

								factorValues.set(row, value);
							}
						}
					}
					break;
				case QUANTITIVE:
				case INTEGER:
				case FLOAT:
					{
						data.put(name, column);
					}
					break;
				default:
					throw new IllegalArgumentException(String.valueOf(type));
			}
		}

		List<RVector<?>> columns = new ArrayList<>(data.values());
		List<String> names = new ArrayList<>(data.keySet());

		return encodeVerificationData(columns, names);
	}

	private FeatureMap ensureFeatureMap(){

		if(this.featureMap == null){
			this.featureMap = loadFeatureMap();
		}

		return this.featureMap;
	}

	private Learner ensureLearner(){

		if(this.learner == null){
			this.learner = loadLearner();
		}

		return this.learner;
	}

	private FeatureMap loadFeatureMap(){
		RGenericVector booster = getObject();

		RVector<?> fmap = DecorationUtil.getVectorElement(booster, "fmap");

		try {
			return loadFeatureMap(fmap);
		} catch(IOException ioe){
			throw new IllegalArgumentException(ioe);
		}
	}

	private Learner loadLearner(){
		RGenericVector booster = getObject();

		RRaw raw = (RRaw)booster.getElement("raw");

		try {
			return loadLearner(raw);
		} catch(IOException ioe){
			throw new IllegalArgumentException(ioe);
		}
	}

	static
	private void checkFeatureMap(FeatureMap featureMap, RVector<?> vector){
		List<FeatureMap.Entry> entries = featureMap.getEntries();

		if(vector.size() != entries.size()){
			throw new IllegalArgumentException("Invalid \'fmap\' element. Expected " + vector.size() + " features, got " + entries.size() + " features");
		}
	}

	static
	private FeatureMap loadFeatureMap(RVector<?> fmap) throws IOException {

		if(fmap instanceof RStringVector){
			return loadFeatureMap((RStringVector)fmap);
		} else

		if(fmap instanceof RGenericVector){
			return loadFeatureMap((RGenericVector)fmap);
		}

		throw new IllegalArgumentException();
	}

	static
	private FeatureMap loadFeatureMap(RStringVector fmap) throws IOException {
		File file = new File(fmap.asScalar());

		try(InputStream is = new FileInputStream(file)){
			return XGBoostUtil.loadFeatureMap(is);
		}
	}

	static
	private FeatureMap loadFeatureMap(RGenericVector fmap){
		RIntegerVector id = fmap.getIntegerValue(0);
		RFactorVector name = fmap.getFactorValue(1);
		RFactorVector type = fmap.getFactorValue(2);

		FeatureMap featureMap = new FeatureMap();

		for(int i = 0; i < id.size(); i++){

			if(i != id.getValue(i)){
				throw new IllegalArgumentException();
			}

			featureMap.addEntry(name.getFactorValue(i), type.getFactorValue(i));
		}

		return featureMap;
	}

	static
	private Learner loadLearner(RRaw raw) throws IOException {
		byte[] value = raw.getValue();

		try(InputStream is = new ByteArrayInputStream(value)){
			return XGBoostUtil.loadLearner(is, ByteOrder.nativeOrder(), null, "$.Model");
		}
	}
}
