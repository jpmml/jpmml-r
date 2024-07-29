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

import org.dmg.pmml.PMML;
import org.jpmml.lightgbm.GBDT;
import org.jpmml.lightgbm.LightGBMUtil;
import org.jpmml.rexp.Converter;
import org.jpmml.rexp.REnvironment;
import org.jpmml.rexp.RExpEncoder;
import org.jpmml.rexp.RRaw;

public class LightGBMConverter extends Converter<REnvironment> {

	public LightGBMConverter(REnvironment environment){
		super(environment);
	}

	@Override
	public PMML encodePMML(RExpEncoder encoder){
		REnvironment environment = getObject();

		RRaw raw = (RRaw)environment.findVariable("raw");
		if(raw == null){
			throw new IllegalArgumentException();
		}

		GBDT gbdt;

		try(InputStream is = new ByteArrayInputStream(raw.getValue())){
			gbdt = LightGBMUtil.loadGBDT(is);
		} catch(IOException ioe){
			throw new IllegalArgumentException(ioe);
		}

		return gbdt.encodePMML(Collections.emptyMap(), null, null);
	}
}