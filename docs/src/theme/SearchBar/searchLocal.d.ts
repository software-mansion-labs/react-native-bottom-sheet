// Typings for the parts of @easyops-cn/docusaurus-search-local's client that
// the search dialog uses. The package ships them as untyped ES modules.

declare module '@easyops-cn/docusaurus-search-local/dist/client/client/theme/searchByWorker' {
  export interface SearchDocument {
    i: number;
    t: string;
    u: string;
    h?: string;
    s?: string;
    b?: string[];
  }
  export interface SearchResult {
    document: SearchDocument;
    type: number;
    page?: SearchDocument | false;
    tokens: string[];
    metadata: Record<string, Record<string, { position: [number, number][] }>>;
  }
  export function fetchIndexesByWorker(
    baseUrl: string,
    searchContext: string
  ): Promise<void>;
  export function searchByWorker(
    baseUrl: string,
    searchContext: string,
    input: string,
    limit: number
  ): Promise<SearchResult[]>;
}

declare module '@easyops-cn/docusaurus-search-local/dist/client/client/utils/highlight' {
  export function highlight(content: string, tokens: string[]): string;
}

declare module '@easyops-cn/docusaurus-search-local/dist/client/client/utils/highlightStemmed' {
  export function highlightStemmed(
    content: string,
    positions: [number, number][],
    tokens: string[],
    maxLength?: number
  ): string;
}

declare module '@easyops-cn/docusaurus-search-local/dist/client/client/utils/getStemmedPositions' {
  export function getStemmedPositions(
    metadata: unknown,
    field: string
  ): [number, number][];
}

declare module '@easyops-cn/docusaurus-search-local/dist/client/client/utils/proxiedGenerated' {
  export const Mark: unknown;
}
