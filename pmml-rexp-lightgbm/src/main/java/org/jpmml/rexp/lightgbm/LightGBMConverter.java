/*
 * Copyright (c) 2024 Villu Ruusmann
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
package org.jpmml.rexp.lightgbm;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.mining.MiningModel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.Label;
import org.jpmml.converter.Schema;
import org.jpmml.lightgbm.GBDT;
import org.jpmml.lightgbm.LightGBMUtil;
import org.jpmml.rexp.ModelConverter;
import org.jpmml.rexp.REnvironment;
import org.jpmml.rexp.RExpEncoder;
import org.jpmml.rexp.RRaw;

public class LightGBMConverter extends ModelConverter<REnvironment> {

	private GBDT gbdt = null;


	public LightGBMConverter(REnvironment environment){
		super(environment);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		GBDT gbdt = ensureGBDT();

		Schema schema = gbdt.encodeSchema(null, null, encoder);

		Label label = schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		encoder.setLabel(label);

		for(Feature feature : features){
			encoder.addFeature(feature);
		}
	}

	@Override
	public MiningModel encodeModel(Schema schema){
		GBDT gbdt = ensureGBDT();

		// XXX
		Map<String, Object> options = Collections.emptyMap();

		Schema lgbmSchema = gbdt.toLightGBMSchema(schema);

		return gbdt.encodeModel(options, lgbmSchema);
	}

	private GBDT ensureGBDT(){

		if(this.gbdt == null){
			this.gbdt = loadGBDT();
		}

		return this.gbdt;
	}

	private GBDT loadGBDT(){
		REnvironment environment = getObject();

		RRaw raw = (RRaw)environment.findVariable("raw");
		if(raw == null){
			throw new IllegalArgumentException();
		}

		try(InputStream is = new ByteArrayInputStream(raw.getValue())){
			return LightGBMUtil.loadGBDT(is);
		} catch(IOException ioe){
			throw new IllegalArgumentException(ioe);
		}
	}
}