package com.gokchu.gokchu.search;

import com.gokchu.gokchu.search.dto.SearchResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 관리자가 유튜브 창을 오가지 않고 앱 안에서 바로 노래를 검색해 큐에 추가할 수 있게 하는 검색 API. */
@RestController
@RequestMapping("/api/tables/{tableToken}")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/search")
    public List<SearchResultResponse> search(@PathVariable UUID tableToken, @RequestParam("q") String query) {
        return searchService.search(tableToken, query);
    }
}
