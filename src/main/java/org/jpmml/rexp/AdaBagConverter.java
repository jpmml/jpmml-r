/*
 * Copyright (c) 2018 Villu Ruusmann
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

abstract
public class AdaBagConverter extends RPartEnsembleConverter<RGenericVector> {

	public AdaBagConverter(RGenericVector object){
		super(object);
	}

	@Override
	public RPartConverter createConverter(RGenericVector rpart){
		return new RPartConverter(rpart){

			@Override
			public boolean hasScoreDistribution(){
				return false;
			}
		};
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector bagging = getObject();

		RGenericVector trees = bagging.getGenericElement("trees");
		RExp terms = bagging.getElement("terms");
		RIntegerVector vardepSummary = bagging.getIntegerAttribute("vardep.summary");

		RExpEncoder termsEncoder = new RExpEncoder();

		FormulaContext content = new EmptyFormulaContext();

		Formula formula = FormulaUtil.createFormula(terms, content, termsEncoder);

		SchemaUtil.setLabel(formula, terms, vardepSummary.names(), encoder);

		encodeTreeSchemas(trees, encoder);
	}
}