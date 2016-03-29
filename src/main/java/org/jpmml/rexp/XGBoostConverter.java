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

import org.dmg.pmml.PMML;
import org.jpmml.xgboost.FeatureMap;
import org.jpmml.xgboost.Learner;
import org.jpmml.xgboost.XGBoostUtil;

public class XGBoostConverter extends Converter {

	@Override
	public PMML convert(RExp rexp){
		return convert((RGenericVector)rexp);
	}

	private PMML convert(RGenericVector booster){
		RRaw raw = (RRaw)booster.getValue("raw");
		RVector<?> fmap = (RVector<?>)booster.getValue("fmap");

		PMML pmml;

		try {
			Learner learner = loadLearner(raw);

			FeatureMap featureMap = loadFeatureMap(fmap);

			pmml = learner.encodePMML(null, null, featureMap);
		} catch(IOException ioe){
			throw new IllegalArgumentException(ioe);
		}

		return pmml;
	}

	static
	private Learner loadLearner(RRaw raw) throws IOException {
		byte[] value = raw.getValue();

		try(InputStream is = new ByteArrayInputStream(value)){
			return XGBoostUtil.loadLearner(is);
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

		FeatureMap featureMap = new FeatureMap();

		for(int i = 0; i < id.size(); i++){
			featureMap.load(String.valueOf(id.getValue(i)), name.getFactorValue(i), type.getFactorValue(i));
		}

		return featureMap;
	}
}