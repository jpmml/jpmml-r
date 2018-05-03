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
package org.jpmml.rexp;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.mining.MiningModel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.Label;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;
import org.jpmml.xgboost.FeatureMap;
import org.jpmml.xgboost.Learner;
import org.jpmml.xgboost.ObjFunction;
import org.jpmml.xgboost.XGBoostUtil;

public class XGBoostConverter extends ModelConverter<RGenericVector> {

	private Learner learner = null;

	private boolean compact = true;


	public XGBoostConverter(RGenericVector booster){
		super(booster);

		this.compact = getOption("compact", Boolean.TRUE);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector booster = getObject();

		RVector<?> fmap = (RVector<?>)DecorationUtil.getValue(booster, "fmap");
		RGenericVector schema = (RGenericVector)booster.getValue("schema", true);

		FeatureMap featureMap;

		try {
			featureMap = loadFeatureMap(fmap);
		} catch(IOException ioe){
			throw new IllegalArgumentException(ioe);
		}

		if(schema != null){
			RVector<?> missing = (RVector<?>)schema.getValue("missing", true);

			if(missing != null){
				featureMap.addMissingValue(ValueUtil.formatValue(missing.asScalar()));
			}
		}

		Learner learner = ensureLearner();

		ObjFunction obj = learner.getObj();

		FieldName targetField = FieldName.create("_target");
		List<String> targetCategories = null;

		if(schema != null){
			RStringVector responseName = (RStringVector)schema.getValue("response_name", true);
			RStringVector responseLevels = (RStringVector)schema.getValue("response_levels", true);

			if(responseName != null){
				targetField = FieldName.create(responseName.asScalar());
			} // End if

			if(responseLevels != null){
				targetCategories = responseLevels.getValues();
			}
		}

		Label label = obj.encodeLabel(targetField, targetCategories, encoder);

		encoder.setLabel(label);

		List<Feature> features = featureMap.encodeFeatures(encoder);

		for(Feature feature : features){
			encoder.addFeature(feature);
		}
	}

	@Override
	public MiningModel encodeModel(Schema schema){
		RGenericVector booster = getObject();

		RNumberVector<?> ntreeLimit = (RNumberVector<?>)booster.getValue("ntreelimit", true);

		Learner learner = ensureLearner();

		Schema xgbSchema = XGBoostUtil.toXGBoostSchema(schema);

		MiningModel miningModel = learner.encodeMiningModel((ntreeLimit != null ? ValueUtil.asInteger(ntreeLimit.asScalar()) : null), this.compact, xgbSchema);

		return miningModel;
	}

	private Learner ensureLearner(){

		if(this.learner == null){
			this.learner = loadLearner();
		}

		return this.learner;
	}

	private Learner loadLearner(){
		RGenericVector booster = getObject();

		RRaw raw = (RRaw)booster.getValue("raw");

		try {
			return loadLearner(raw);
		} catch(IOException ioe){
			throw new IllegalArgumentException(ioe);
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
		RIntegerVector id = (RIntegerVector)fmap.getValue(0);
		RIntegerVector name = (RIntegerVector)fmap.getValue(1);
		RIntegerVector type = (RIntegerVector)fmap.getValue(2);

		if(!name.isFactor() || !type.isFactor()){
			throw new IllegalArgumentException();
		}

		FeatureMap featureMap = new FeatureMap();

		for(int i = 0; i < id.size(); i++){

			if(i != id.getValue(i)){
				throw new IllegalArgumentException();
			}

			featureMap.addEntry(name.getLevelValue(i), type.getLevelValue(i));
		}

		return featureMap;
	}

	static
	private Learner loadLearner(RRaw raw) throws IOException {
		byte[] value = raw.getValue();

		try(InputStream is = new ByteArrayInputStream(value)){
			return XGBoostUtil.loadLearner(is);
		}
	}
}
