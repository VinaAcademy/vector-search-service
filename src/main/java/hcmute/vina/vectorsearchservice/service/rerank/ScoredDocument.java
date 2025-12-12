package hcmute.vina.vectorsearchservice.service.rerank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoredDocument {
	private int index;
	private String text;
	private float score;
	
}
 